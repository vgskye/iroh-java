use proc_macro::TokenStream;
use proc_macro2::{Span as FakeSpan, TokenStream as FakeTokenStream};
use quote::quote;
use syn::{
    FnArg, GenericArgument, Ident, ItemFn, Pat, PatIdent, PathArguments, ReturnType, Token, Type,
    parse_macro_input, punctuated::Punctuated,
};

#[proc_macro_attribute]
pub fn export(attr: TokenStream, item: TokenStream) -> TokenStream {
    let attr = FakeTokenStream::from(attr);
    let func = parse_macro_input!(item as ItemFn);

    let name = &func.sig.ident;

    let ReturnType::Type(_, ty) = &func.sig.output else {
        return TokenStream::from(quote! {
            ::core::compile_error!("function should return a Result");
        });
    };
    let Type::Path(ty) = &**ty else {
        return TokenStream::from(quote! {
            ::core::compile_error!("function should return a Result");
        });
    };
    let Some(ty) = ty.path.segments.last() else {
        return TokenStream::from(quote! {
            ::core::compile_error!("function should return a Result");
        });
    };
    if ty.ident != "Result" {
        return TokenStream::from(quote! {
            ::core::compile_error!("function should return a Result");
        });
    }
    let PathArguments::AngleBracketed(ty) = &ty.arguments else {
        return TokenStream::from(quote! {
            ::core::compile_error!("function should return a Result");
        });
    };
    let Some(GenericArgument::Type(ty)) = ty.args.first() else {
        return TokenStream::from(quote! {
            ::core::compile_error!("function should return a Result");
        });
    };
    let generics = &func.sig.generics;
    let mut args = func.sig.inputs.clone();
    let mut invoke: Punctuated<Ident, Token![,]> = Punctuated::new();
    for (i, ele) in args.iter_mut().enumerate() {
        let FnArg::Typed(arg) = ele else {
            continue;
        };
        invoke.push(Ident::new(&format!("ident{i}"), FakeSpan::call_site()));
        arg.pat = Box::new(Pat::Ident(PatIdent {
            attrs: vec![],
            by_ref: None,
            mutability: None,
            ident: Ident::new(&format!("ident{i}"), FakeSpan::call_site()),
            subpat: None,
        }));
    }
    let Some(FnArg::Typed(arg)) = args.first_mut() else {
        return TokenStream::from(quote! {
            ::core::compile_error!("function should have a &mut T as the first argument");
        });
    };
    let Type::Reference(refty) = &*arg.ty else {
        return TokenStream::from(quote! {
            ::core::compile_error!("function should have a &mut T as the first argument");
        });
    };
    arg.ty = refty.elem.clone();

    let out = quote! {
        #[unsafe(no_mangle)]
        pub extern "system" fn #attr #generics (mut #args) -> #ty {
            match #name(&mut #invoke) {
                ::core::result::Result::Ok(value) => value,
                ::core::result::Result::Err(e) => {
                    if let Err(e2) = ident0.throw_new("link/e4mc/iroh/NativeException", e.to_string()) {
                        ::log::error!("Error throwing exception for {e}: {e2}")
                    }
                    ::core::default::Default::default()
                }
            }
        }

        #[inline(always)]
        #func
    };

    TokenStream::from(out)
}
