/*! highlight.js v9.13.1 | BSD3 License | git.io/hljslicense */
/* Only protobuf */
hljs.registerLanguage("protobuf",function(e){return{k:{keyword:"package import option optional required repeated group oneof",built_in:"double float int32 int64 uint32 uint64 sint32 sint64 fixed32 fixed64 sfixed32 sfixed64 bool string bytes",literal:"true false"},c:[e.QSM,e.NM,e.CLCM,{cN:"class",bK:"message enum service",e:/\{/,i:/\n/,c:[e.inherit(e.TM,{starts:{eW:!0,eE:!0}})]},{cN:"function",bK:"rpc",e:/;/,eE:!0,k:"rpc returns"},{b:/^\s*[A-Z_]+/,e:/\s*=/,eE:!0}]}});

window.addEventListener("load", function(event) {
  document.querySelectorAll("code.language-protobuf").forEach(e => hljs.highlightBlock(e));
});
