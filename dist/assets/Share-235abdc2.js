import{_ as E,D as F,a as j,r,e as h,f as o,h as _,i as s,j as b,w as f,n as k,l as v,F as x,m,t as g,q as z,s as H}from"./index-baa31b2b.js";const U=i=>(z("data-v-138699d9"),i=i(),H(),i),A={class:"top"},G=U(()=>s("span",{class:"iconfont icon-cancel"},null,-1)),J={class:"file-list"},K=["onMouseenter","onMouseleave"],P=["title"],Q={class:"op"},W=["onClick"],X=["onClick"],Y={__name:"Share",setup(i){const{toClipboard:C}=F(),{proxy:c}=j(),y={loadDataList:"/share/loadShareList",cancelShare:"/share/cancelShare"},I=[{label:"文件名",prop:"fileName",scopedSlots:"fileName"},{label:"分享时间",prop:"shareTime",width:200},{label:"失效时间",prop:"expireTime",scopedSlots:"expireTime",width:200},{label:"浏览次数",prop:"showCount",width:200}],l=r({}),N={extHeight:20,selectType:"checkbox"},S=async()=>{let a={pageNo:l.value.pageNo,pageSize:l.value.pageSize},t=await c.Request({url:y.loadDataList,params:a});t&&(l.value=t.data)},n=r([]),D=a=>{n.value=[],a.forEach(t=>{n.value.push(t.shareId)})},O=a=>{l.value.list.forEach(t=>{t.showOp=!1}),a.showOp=!0},$=a=>{a.showOp=!1},L=r(document.location.origin+"/share/"),M=async a=>{await C(`链接:${L.value}${a.shareId} 提取码:${a.code}`),c.Message.success("复制成功")},p=r([]),B=()=>{n.value.length!=0&&(p.value=n.value,T())},w=a=>{p.value=[a.shareId],T()},T=()=>{c.Confirm("你确定要取消分享吗?",async()=>{await c.Request({url:y.cancelShare,params:{shareIds:p.value.join(",")}})&&(c.Message.success("取消分享成功"),S())})};return(a,t)=>{const R=h("el-button"),d=h("Icon"),V=h("Table");return o(),_("div",null,[s("div",A,[b(R,{type:"primary",disabled:n.value.length==0,onClick:B},{default:f(()=>[G,k(" 取消分享")]),_:1},8,["disabled"])]),s("div",J,[b(V,{ref:"dataTableRef",columns:I,dataSource:l.value,fetch:S,initFetch:!0,options:N,onRowSelected:D},{fileName:f(({index:q,row:e})=>[s("div",{class:"file-item",onMouseenter:u=>O(e),onMouseleave:u=>$(e)},[(e.fileType==3||e.fileType==1)&&e.status!==0?(o(),v(d,{key:0,cover:e.fileCover},null,8,["cover"])):(o(),_(x,{key:1},[e.folderType==0?(o(),v(d,{key:0,fileType:e.fileType},null,8,["fileType"])):m("",!0),e.folderType==1?(o(),v(d,{key:1,fileType:0})):m("",!0)],64)),s("span",{class:"file-name",title:e.fileName},g(e.fileName),9,P),s("span",Q,[e.showOp?(o(),_(x,{key:0},[s("span",{class:"iconfont icon-link",onClick:u=>M(e)},"复制链接",8,W),s("span",{class:"iconfont icon-cancel",onClick:u=>w(e)},"取消分享",8,X)],64)):m("",!0)])],40,K)]),expireTime:f(({index:q,row:e})=>[k(g(e.validType==3?"永久":e.expireTime),1)]),_:1},8,["dataSource"])])])}}},ee=E(Y,[["__scopeId","data-v-138699d9"]]);export{ee as default};
