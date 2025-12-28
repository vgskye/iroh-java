use std::ptr::null_mut;
use std::str::FromStr;
use std::sync::Arc;

use anyhow::anyhow;
use crossbeam_channel::Receiver as MpscReceiver;
use crossbeam_channel::Sender as MpscSender;
use crossbeam_channel::unbounded as mpsc_channel;
use iroh::Endpoint;
use iroh::EndpointAddr;
use iroh::endpoint::Connection;
use iroh::endpoint::RecvStream;
use iroh::endpoint::SendStream;
use iroh::endpoint::VarInt;
use iroh_tickets::endpoint::EndpointTicket;
use jni::objects::JByteBuffer;
use jni::objects::JString;
use jni::sys::jint;
use jni::sys::jlong;
use jni::sys::jsize;
use tokio::runtime::Runtime;

use iroh_java_proc_macros::export;
use jni::objects::JByteArray;
use jni::objects::JObjectArray;
use jni::objects::JValue;
use jni::{
    JNIEnv,
    objects::{GlobalRef, JClass, JObject},
};
use tokio::sync::Mutex;
use zerocopy::IntoBytes;

trait JavaWrapped: Sized {
    unsafe fn write_to_jobject<'other_local>(
        self,
        env: &mut JNIEnv<'_>,
        object: impl AsRef<JObject<'other_local>>,
    ) -> jni::errors::Result<()>;

    unsafe fn get_from_jobject<'a, 'local, 'other_local>(
        env: &'a mut JNIEnv<'local>,
        object: impl AsRef<JObject<'other_local>>,
    ) -> jni::errors::Result<std::sync::MutexGuard<'a, Self>>;

    unsafe fn take_from_jobject<'other_local>(
        env: &mut JNIEnv<'_>,
        object: impl AsRef<JObject<'other_local>>,
    ) -> jni::errors::Result<Self>;
}

macro_rules! impl_java_wrapped {
    ($type:ty, $field_name:literal) => {
        impl JavaWrapped for $type {
            unsafe fn write_to_jobject<'other_local>(
                self,
                env: &mut JNIEnv<'_>,
                object: impl AsRef<JObject<'other_local>>,
            ) -> jni::errors::Result<()> {
                unsafe { env.set_rust_field(object, $field_name, self) }
            }

            unsafe fn get_from_jobject<'a, 'local, 'other_local>(
                env: &'a mut JNIEnv<'local>,
                object: impl AsRef<JObject<'other_local>>,
            ) -> jni::errors::Result<std::sync::MutexGuard<'a, Self>> {
                unsafe { env.get_rust_field(object, $field_name) }
            }

            unsafe fn take_from_jobject<'other_local>(
                env: &mut JNIEnv<'_>,
                object: impl AsRef<JObject<'other_local>>,
            ) -> jni::errors::Result<Self> {
                unsafe { env.take_rust_field(object, $field_name) }
            }
        }
    };
}

type DeferredInitializer =
    Box<dyn for<'local> FnOnce(&mut JNIEnv<'local>) -> anyhow::Result<JObject<'local>> + Send>;

impl_java_wrapped!(DeferredInitializer, "ptrDeferredObjectMaker");

#[export(Java_link_e4mc_iroh_Native_resolveDeferredInitializer)]
fn resolve_deferred_initializer<'local>(
    env: &mut JNIEnv<'local>,
    class: JClass<'local>,
    deferred: JObject<'local>,
) -> anyhow::Result<JObject<'local>> {
    let deferred: DeferredInitializer =
        unsafe { DeferredInitializer::take_from_jobject(env, deferred)? };
    deferred(env)
}

struct CallbackResult {
    handle: GlobalRef,
    object: anyhow::Result<DeferredInitializer>,
    cleanup: Option<Box<dyn FnOnce() + Send>>,
}

#[derive(Clone)]
struct EndpointBundle {
    runtime: Arc<Runtime>,
    endpoint: Endpoint,
    sender: MpscSender<Option<CallbackResult>>,
}

struct CallbackHandle {
    future: GlobalRef,
    sender: MpscSender<Option<CallbackResult>>,
}

impl CallbackHandle {
    fn resolve(
        self,
        object: impl for<'local> FnOnce(&mut JNIEnv<'local>) -> anyhow::Result<JObject<'local>>
        + Send
        + 'static,
    ) {
        _ = self.sender.send(Some(CallbackResult {
            handle: self.future,
            object: Ok(Box::new(object)),
            cleanup: None,
        }));
    }

    fn reject(self, error: impl Into<anyhow::Error>) {
        _ = self.sender.send(Some(CallbackResult {
            handle: self.future,
            object: Err(error.into()),
            cleanup: None,
        }));
    }

    fn reject_with_cleanup(
        self,
        error: impl Into<anyhow::Error>,
        cleanup: impl FnOnce() + Send + 'static,
    ) {
        _ = self.sender.send(Some(CallbackResult {
            handle: self.future,
            object: Err(error.into()),
            cleanup: Some(Box::new(cleanup)),
        }));
    }
}

impl_java_wrapped!(EndpointBundle, "ptrEndpointBundle");

type CallbackReceiver = MpscReceiver<Option<CallbackResult>>;

impl_java_wrapped!(CallbackReceiver, "ptrCallbackReceiver");

#[export(Java_link_e4mc_iroh_Native_initEndpointBundle)]
fn init_endpoint_bundle<'local>(
    env: &mut JNIEnv<'local>,
    class: JClass<'local>,
    bundle: JObject<'local>,
    alpns: JObjectArray<'local>,
) -> anyhow::Result<()> {
    let alpns_len = env.get_array_length(&alpns)?;
    let mut alpns_vec = Vec::with_capacity(alpns_len as usize);
    for i in 0..alpns_len {
        let entry = env.get_object_array_element(&alpns, i)?;
        alpns_vec.push(env.convert_byte_array(JByteArray::from(entry))?);
    }
    let runtime = Arc::new(tokio::runtime::Runtime::new()?);
    // TODO: more comprehensive options for Endpoint configuration
    let endpoint = runtime.block_on(
        Endpoint::empty_builder(iroh::RelayMode::Default)
            .alpns(alpns_vec)
            .proxy_from_env()
            .bind(),
    )?;
    let (sender, receiver) = mpsc_channel();
    let bundle_struct = EndpointBundle {
        runtime,
        endpoint,
        sender,
    };
    unsafe {
        bundle_struct.write_to_jobject(env, &bundle)?;
        receiver.write_to_jobject(env, &bundle)?;
    }
    Ok(())
}

#[export(Java_link_e4mc_iroh_Native_freeEndpointBundle)]
fn free_endpoint_bundle<'local>(
    env: &mut JNIEnv<'local>,
    class: JClass<'local>,
    bundle: JObject<'local>,
) -> anyhow::Result<()> {
    unsafe {
        let bundle_struct = EndpointBundle::take_from_jobject(env, &bundle)?;
        _ = bundle_struct.sender.send(None);
        bundle_struct
            .runtime
            .block_on(bundle_struct.endpoint.close());
        drop(bundle_struct);
        drop(CallbackReceiver::take_from_jobject(env, &bundle)?);
    }
    Ok(())
}

#[export(Java_link_e4mc_iroh_Native_pollEndpointBundle)]
fn poll_endpoint_bundle<'local>(
    env: &mut JNIEnv<'local>,
    class: JClass<'local>,
    bundle: JObject<'local>,
) -> anyhow::Result<JObject<'local>> {
    let receiver = unsafe { CallbackReceiver::get_from_jobject(env, &bundle)? };
    let polled = receiver.recv()?;
    drop(receiver);
    let Some(polled) = polled else {
        return Err(anyhow!("shutting down"));
    };
    let handle = env.new_local_ref(polled.handle)?;
    Ok(match polled.object {
        Ok(maker) => {
            let deferred = env.new_object("link/e4mc/iroh/DeferredInitializer", "()V", &[])?;
            unsafe {
                maker.write_to_jobject(env, &deferred)?;
            }
            env.new_object(
                "link/e4mc/iroh/CallbackResolve",
                "(Ljava/util/concurrent/CompletableFuture;Llink/e4mc/iroh/DeferredInitializer;)V",
                &[JValue::Object(&handle), JValue::Object(&deferred)],
            )?
        }
        Err(e) => {
            let cause = env.new_string(e.to_string())?;
            let exc = env.new_object(
                "link/e4mc/iroh/NativeException",
                "(Ljava/lang/String;)V",
                &[JValue::Object(&cause)],
            )?;
            env.new_object(
                "link/e4mc/iroh/CallbackReject",
                "(Ljava/util/concurrent/CompletableFuture;Ljava/lang/Throwable;)V",
                &[JValue::Object(&handle), JValue::Object(&exc)],
            )?
        }
    })
}

#[export(Java_link_e4mc_iroh_Native_addrEndpointBundle)]
fn addr_endpoint_bundle<'local>(
    env: &mut JNIEnv<'local>,
    class: JClass<'local>,
    bundle: JObject<'local>,
) -> anyhow::Result<JString<'local>> {
    let bundle = unsafe { EndpointBundle::get_from_jobject(env, &bundle)? };
    let addr = bundle.endpoint.addr();
    drop(bundle);
    let ticket = EndpointTicket::new(addr);
    Ok(env.new_string(ticket.to_string())?)
}

#[export(Java_link_e4mc_iroh_Native_onlineEndpointBundle)]
fn online_endpoint_bundle<'local>(
    env: &mut JNIEnv<'local>,
    class: JClass<'local>,
    bundle: JObject<'local>,
    future: JObject<'local>,
) -> anyhow::Result<()> {
    let bundle = unsafe { EndpointBundle::get_from_jobject(env, &bundle)? }.clone();
    let handle = CallbackHandle {
        future: env.new_global_ref(future)?,
        sender: bundle.sender,
    };
    bundle.runtime.spawn(async move {
        bundle.endpoint.online().await;
        handle.resolve(move |env| {
            let addr = bundle.endpoint.addr();
            let ticket = EndpointTicket::new(addr);
            Ok(env.new_string(ticket.to_string())?.into())
        });
    });
    Ok(())
}

#[export(Java_link_e4mc_iroh_Native_closeEndpointBundle)]
fn close_endpoint_bundle<'local>(
    env: &mut JNIEnv<'local>,
    class: JClass<'local>,
    bundle: JObject<'local>,
    future: JObject<'local>,
) -> anyhow::Result<()> {
    let bundle = unsafe { EndpointBundle::get_from_jobject(env, &bundle)? }.clone();
    let handle = CallbackHandle {
        future: env.new_global_ref(future)?,
        sender: bundle.sender,
    };
    bundle.runtime.spawn(async move {
        bundle.endpoint.close().await;
        handle.resolve(|_| Ok(JObject::null()));
    });
    Ok(())
}

#[export(Java_link_e4mc_iroh_Native_acceptEndpointBundle)]
fn accept_endpoint_bundle<'local>(
    env: &mut JNIEnv<'local>,
    class: JClass<'local>,
    bundle: JObject<'local>,
    future: JObject<'local>,
) -> anyhow::Result<()> {
    let bundle = unsafe { EndpointBundle::get_from_jobject(env, &bundle)? }.clone();
    let handle = CallbackHandle {
        future: env.new_global_ref(future)?,
        sender: bundle.sender.clone(),
    };
    async fn inner(bundle: EndpointBundle) -> anyhow::Result<Option<IrohConnection>> {
        match bundle.endpoint.accept().await {
            Some(conn) => Ok(Some(IrohConnection {
                runtime: bundle.runtime,
                connection: conn.accept()?.await?,
                sender: bundle.sender,
            })),
            None => Ok(None),
        }
    }
    bundle.runtime.clone().spawn(async move {
        match inner(bundle).await {
            Ok(None) => handle.resolve(|_| Ok(JObject::null())),
            Ok(Some(conn)) => handle.resolve(|env| {
                let obj = env.new_object("link/e4mc/iroh/Connection", "()V", &[])?;
                unsafe {
                    conn.write_to_jobject(env, &obj)?;
                }
                Ok(obj)
            }),
            Err(e) => handle.reject(e),
        }
    });
    Ok(())
}

#[export(Java_link_e4mc_iroh_Native_connectEndpointBundle)]
fn connect_endpoint_bundle<'local>(
    env: &mut JNIEnv<'local>,
    class: JClass<'local>,
    bundle: JObject<'local>,
    addr: JString<'local>,
    alpn: JByteArray<'local>,
    future: JObject<'local>,
) -> anyhow::Result<()> {
    let bundle = unsafe { EndpointBundle::get_from_jobject(env, &bundle)? }.clone();
    let handle = CallbackHandle {
        future: env.new_global_ref(future)?,
        sender: bundle.sender.clone(),
    };
    let alpn = env.convert_byte_array(alpn)?;
    let addr: String = env.get_string(&addr)?.into();
    let addr: EndpointAddr = EndpointTicket::from_str(&addr)?.into();
    bundle.runtime.clone().spawn(async move {
        match bundle.endpoint.connect(addr, &alpn).await {
            Ok(conn) => {
                let conn = IrohConnection {
                    runtime: bundle.runtime,
                    connection: conn,
                    sender: bundle.sender,
                };
                handle.resolve(|env| {
                    let obj = env.new_object("link/e4mc/iroh/Connection", "()V", &[])?;
                    unsafe {
                        conn.write_to_jobject(env, &obj)?;
                    }
                    Ok(obj)
                })
            }
            Err(e) => handle.reject(e),
        }
    });
    Ok(())
}

#[derive(Clone)]
struct IrohConnection {
    runtime: Arc<Runtime>,
    connection: Connection,
    sender: MpscSender<Option<CallbackResult>>,
}

impl_java_wrapped!(IrohConnection, "ptrIrohConnection");

#[export(Java_link_e4mc_iroh_Native_closeIrohConnection)]
fn close_iroh_connection<'local>(
    env: &mut JNIEnv<'local>,
    class: JClass<'local>,
    connection: JObject<'local>,
    code: jlong,
    reason: JByteArray<'local>,
) -> anyhow::Result<()> {
    let code = VarInt::from_u64(code as u64)?;
    let reason = env.convert_byte_array(reason)?;
    let connection = unsafe { IrohConnection::take_from_jobject(env, &connection)? };
    connection.connection.close(code, &reason);
    drop(connection);
    Ok(())
}

#[export(Java_link_e4mc_iroh_Native_addrIrohConnection)]
fn addr_iroh_connection<'local>(
    env: &mut JNIEnv<'local>,
    class: JClass<'local>,
    connection: JObject<'local>,
) -> anyhow::Result<JString<'local>> {
    let connection = unsafe { IrohConnection::get_from_jobject(env, &connection)? };
    let ticket: EndpointAddr = connection.connection.remote_id().into();
    let ticket = EndpointTicket::new(ticket);
    drop(connection);
    Ok(env.new_string(ticket.to_string())?)
}

#[export(Java_link_e4mc_iroh_Native_acceptBiIrohConnection)]
fn accept_bi_connection<'local>(
    env: &mut JNIEnv<'local>,
    class: JClass<'local>,
    connection: JObject<'local>,
    future: JObject<'local>,
) -> anyhow::Result<()> {
    let future = env.new_global_ref(future)?;
    let connection = unsafe { IrohConnection::get_from_jobject(env, &connection)? }.clone();
    let handle = CallbackHandle {
        future,
        sender: connection.sender.clone(),
    };
    connection.runtime.clone().spawn(async move {
        match connection.connection.accept_bi().await {
            Ok((send, recv)) => handle.resolve(|env| {
                let obj = env.new_object("link/e4mc/iroh/Stream", "()V", &[])?;
                let stream = IrohStream {
                    send: Some(Arc::new(Mutex::new(send))),
                    recv: Some(Arc::new(Mutex::new(recv))),
                    runtime: connection.runtime,
                    sender: connection.sender,
                };
                unsafe {
                    stream.write_to_jobject(env, &obj)?;
                }
                Ok(obj)
            }),
            Err(e) => handle.reject(e),
        }
    });
    Ok(())
}

#[export(Java_link_e4mc_iroh_Native_acceptUniIrohConnection)]
fn accept_uni_connection<'local>(
    env: &mut JNIEnv<'local>,
    class: JClass<'local>,
    connection: JObject<'local>,
    future: JObject<'local>,
) -> anyhow::Result<()> {
    let future = env.new_global_ref(future)?;
    let connection = unsafe { IrohConnection::get_from_jobject(env, &connection)? }.clone();
    let handle = CallbackHandle {
        future,
        sender: connection.sender.clone(),
    };
    connection.runtime.clone().spawn(async move {
        match connection.connection.accept_uni().await {
            Ok(recv) => handle.resolve(|env| {
                let obj = env.new_object("link/e4mc/iroh/Stream", "()V", &[])?;
                let stream = IrohStream {
                    send: None,
                    recv: Some(Arc::new(Mutex::new(recv))),
                    runtime: connection.runtime,
                    sender: connection.sender,
                };
                unsafe {
                    stream.write_to_jobject(env, &obj)?;
                }
                Ok(obj)
            }),
            Err(e) => handle.reject(e),
        }
    });
    Ok(())
}

#[export(Java_link_e4mc_iroh_Native_openBiIrohConnection)]
fn open_bi_connection<'local>(
    env: &mut JNIEnv<'local>,
    class: JClass<'local>,
    connection: JObject<'local>,
    future: JObject<'local>,
) -> anyhow::Result<()> {
    let future = env.new_global_ref(future)?;
    let connection = unsafe { IrohConnection::get_from_jobject(env, &connection)? }.clone();
    let handle = CallbackHandle {
        future,
        sender: connection.sender.clone(),
    };
    connection.runtime.clone().spawn(async move {
        match connection.connection.open_bi().await {
            Ok((send, recv)) => handle.resolve(|env| {
                let obj = env.new_object("link/e4mc/iroh/Stream", "()V", &[])?;
                let stream = IrohStream {
                    send: Some(Arc::new(Mutex::new(send))),
                    recv: Some(Arc::new(Mutex::new(recv))),
                    runtime: connection.runtime,
                    sender: connection.sender,
                };
                unsafe {
                    stream.write_to_jobject(env, &obj)?;
                }
                Ok(obj)
            }),
            Err(e) => handle.reject(e),
        }
    });
    Ok(())
}

#[export(Java_link_e4mc_iroh_Native_openUniIrohConnection)]
fn open_uni_connection<'local>(
    env: &mut JNIEnv<'local>,
    class: JClass<'local>,
    connection: JObject<'local>,
    future: JObject<'local>,
) -> anyhow::Result<()> {
    let future = env.new_global_ref(future)?;
    let connection = unsafe { IrohConnection::get_from_jobject(env, &connection)? }.clone();
    let handle = CallbackHandle {
        future,
        sender: connection.sender.clone(),
    };
    connection.runtime.clone().spawn(async move {
        match connection.connection.open_uni().await {
            Ok(send) => handle.resolve(|env| {
                let obj = env.new_object("link/e4mc/iroh/Stream", "()V", &[])?;
                let stream = IrohStream {
                    send: Some(Arc::new(Mutex::new(send))),
                    recv: None,
                    runtime: connection.runtime,
                    sender: connection.sender,
                };
                unsafe {
                    stream.write_to_jobject(env, &obj)?;
                }
                Ok(obj)
            }),
            Err(e) => handle.reject(e),
        }
    });
    Ok(())
}

#[derive(Clone)]
struct IrohStream {
    send: Option<Arc<Mutex<SendStream>>>,
    recv: Option<Arc<Mutex<RecvStream>>>,
    runtime: Arc<Runtime>,
    sender: MpscSender<Option<CallbackResult>>,
}

impl_java_wrapped!(IrohStream, "ptrIrohStream");

struct MutU8ButSend(*mut u8);

unsafe impl Send for MutU8ButSend {}

#[export(Java_link_e4mc_iroh_Native_freeIrohStream)]
fn free_iroh_stream<'local>(
    env: &mut JNIEnv<'local>,
    class: JClass<'local>,
    stream: JObject<'local>,
) -> anyhow::Result<()> {
    let stream = unsafe { IrohStream::take_from_jobject(env, stream)? };
    drop(stream);
    Ok(())
}

#[export(Java_link_e4mc_iroh_Native_readIrohStreamByteBuffer)]
fn read_iroh_stream_bytebuffer<'local>(
    env: &mut JNIEnv<'local>,
    class: JClass<'local>,
    stream: JObject<'local>,
    buffer: JByteBuffer<'local>,
    offset: jlong,
    maxlen: jlong,
    future: JObject<'local>,
) -> anyhow::Result<()> {
    let persist = env.new_global_ref(&buffer)?;
    let addr = MutU8ButSend(env.get_direct_buffer_address(&buffer)?);
    let len = env.get_direct_buffer_capacity(&buffer)?;
    if len < (offset as usize) {
        return Err(anyhow!(
            "ByteBuffer too small! Requested offset is {offset}, but it's only {len} long!"
        ));
    }
    let maxlen = (len - offset as usize).min(maxlen as usize);
    let future = env.new_global_ref(future)?;
    let stream = unsafe { IrohStream::get_from_jobject(env, stream)? };
    let handle = CallbackHandle {
        future,
        sender: stream.sender.clone(),
    };
    if let Some(recv) = stream.recv.clone() {
        stream.runtime.spawn(async move {
            let mut recv = recv.lock().await;
            match recv.read_chunk(maxlen, true).await {
                Ok(None) => handle.resolve(move |_| {
                    drop(persist);
                    Ok(JObject::null())
                }),
                Ok(Some(chunk)) => {
                    let addr = addr;
                    unsafe {
                        std::ptr::copy_nonoverlapping(
                            chunk.bytes.as_ptr(),
                            addr.0.byte_add(offset as usize),
                            chunk.bytes.len(),
                        );
                    }
                    let read = chunk.bytes.len();
                    handle.resolve(move |env| {
                        drop(persist);
                        Ok(env
                            .call_static_method(
                                "java/lang/Long",
                                "valueOf",
                                "(J)Ljava/lang/Long;",
                                &[JValue::Long(read as i64)],
                            )?
                            .l()?)
                    })
                }
                Err(e) => handle.reject_with_cleanup(e, move || drop(persist)),
            }
        });
    }
    Ok(())
}

#[export(Java_link_e4mc_iroh_Native_readIrohStreamByteArray)]
fn read_iroh_stream_bytearray<'local>(
    env: &mut JNIEnv<'local>,
    class: JClass<'local>,
    stream: JObject<'local>,
    maxlen: jlong,
    future: JObject<'local>,
) -> anyhow::Result<()> {
    let future = env.new_global_ref(future)?;
    let stream = unsafe { IrohStream::get_from_jobject(env, stream)? };
    let handle = CallbackHandle {
        future,
        sender: stream.sender.clone(),
    };
    if let Some(recv) = stream.recv.clone() {
        stream.runtime.spawn(async move {
            let mut recv = recv.lock().await;
            match recv.read_chunk(maxlen as usize, true).await {
                Ok(None) => handle.resolve(|_| Ok(JObject::null())),
                Ok(Some(chunk)) => {
                    handle.resolve(move |env| Ok(env.byte_array_from_slice(&chunk.bytes)?.into()))
                }
                Err(e) => handle.reject(e),
            }
        });
    }
    Ok(())
}

#[export(Java_link_e4mc_iroh_Native_writeIrohStreamByteBuffer)]
fn write_iroh_stream_bytebuffer<'local>(
    env: &mut JNIEnv<'local>,
    class: JClass<'local>,
    stream: JObject<'local>,
    buffer: JByteBuffer<'local>,
    offset: jlong,
    len: jlong,
    future: JObject<'local>,
) -> anyhow::Result<()> {
    let persist = env.new_global_ref(&buffer)?;
    let addr = env.get_direct_buffer_address(&buffer)?;
    let cap = env.get_direct_buffer_capacity(&buffer)?;
    if cap < ((offset as usize) + (len as usize)) {
        return Err(anyhow!(
            "ByteBuffer too small! Requested offset and length are {offset} and {len}, but it's only {cap} long!"
        ));
    }
    let future = env.new_global_ref(future)?;
    let buf = unsafe { std::slice::from_raw_parts(addr.byte_add(offset as usize), len as usize) };
    let stream = unsafe { IrohStream::get_from_jobject(env, stream)? };
    let handle = CallbackHandle {
        future,
        sender: stream.sender.clone(),
    };
    if let Some(send) = stream.send.clone() {
        stream.runtime.spawn(async move {
            let mut send = send.lock().await;
            match send.write_all(buf).await {
                Ok(()) => handle.resolve(move |_| {
                    drop(persist);
                    Ok(JObject::null())
                }),
                Err(e) => handle.reject_with_cleanup(e, move || drop(persist)),
            }
        });
    }
    Ok(())
}

#[export(Java_link_e4mc_iroh_Native_writeIrohStreamByteArray)]
fn write_iroh_stream_bytearray<'local>(
    env: &mut JNIEnv<'local>,
    class: JClass<'local>,
    stream: JObject<'local>,
    array: JByteArray<'local>,
    offset: jlong,
    len: jlong,
    future: JObject<'local>,
) -> anyhow::Result<()> {
    let future = env.new_global_ref(future)?;
    let mut buf = vec![0i8; len as usize];
    env.get_byte_array_region(array, offset as jsize, &mut buf)?;
    let stream = unsafe { IrohStream::get_from_jobject(env, stream)? };
    let handle = CallbackHandle {
        future,
        sender: stream.sender.clone(),
    };
    if let Some(send) = stream.send.clone() {
        stream.runtime.spawn(async move {
            let mut send = send.lock().await;
            match send.write_all(buf.as_bytes()).await {
                Ok(()) => handle.resolve(move |_| Ok(JObject::null())),
                Err(e) => handle.reject(e),
            }
        });
    }
    Ok(())
}
