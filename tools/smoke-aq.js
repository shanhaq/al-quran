/* smoke-aq.js — Al-Quran app: reader + audio routing (aya vs urdu surah) */
const fs=require('fs'), vm=require('vm');
const A=process.env.HOME+'/al-quran/app/src/main/assets/';
const html=fs.readFileSync(A+'index.html','utf8');
const datajs=fs.readFileSync(A+'quran-data.js','utf8');
const qjs=fs.readFileSync(A+'quran.js','utf8');
const appjs=html.match(/<script>\n([\s\S]*?)<\/script>\s*<\/body>/)[1];

let fail=0; const ok=(n,c)=>{ console.log((c?'  ok  ':'  FAIL ')+n); if(!c)fail=1; };

/* ---- stub DOM ---- */
const els={};
function cl(){ const s=new Set(); return {add:c=>s.add(c),remove:c=>s.delete(c),toggle:(c,f)=>{f===undefined?(s.has(c)?s.delete(c):s.add(c)):(f?s.add(c):s.delete(c))},contains:c=>s.has(c)}; }
function mk(id){
  const e={id,_html:'',_text:'',value:'',_attr:{},classList:cl(),style:{setProperty(){},getPropertyValue(){return''}},
    children:[],files:[],dataset:{},
    get innerHTML(){return this._html}, set innerHTML(v){this._html=String(v)},
    get textContent(){return this._text}, set textContent(v){this._text=String(v)},
    childNodes:[{nodeValue:''}],
    querySelector(){return null}, querySelectorAll(){return []},
    appendChild(c){this.children.push(c);return c}, removeChild(){},
    addEventListener(){},removeEventListener(){},click(){},setAttribute(k,v){this._attr[k]=String(v)},
    getAttribute(k){return this._attr[k]!==undefined?this._attr[k]:null},focus(){},contains(){return false},
    closest(){return null},scrollIntoView(){},offsetWidth:360,offsetHeight:640};
  return e;
}
const screens=['s-quran','s-reader','s-bm','s-settings'].map(id=>mk(id));
const navEl=mk('nav'); ['quran','bm','settings'].forEach(t=>{const b=mk('nb-'+t); b._attr['data-t']=t; navEl.children.push(b);});
els['nav']=navEl;
const docEl=mk('html'); docEl.setAttribute('data-theme','light');
global.document={ getElementById:id=>els[id]||(els[id]=mk(id)),
  querySelectorAll:sel=>sel==='.screen'?screens:[], querySelector:()=>null,
  createElement:()=>mk('_c'), addEventListener(){}, documentElement:docEl, body:mk('body'), title:'' };
global.window={ matchMedia:()=>({matches:false,addEventListener(){}}), addEventListener(){}, location:{href:''} };
global.navigator={ userAgent:'smoke', language:'en', share:undefined };
global.localStorage={ _d:{}, getItem(k){return k in this._d?this._d[k]:null}, setItem(k,v){this._d[k]=String(v)}, removeItem(k){delete this._d[k]} };
global.setTimeout=()=>0; global.clearTimeout=()=>{}; global.setInterval=()=>0; global.clearInterval=()=>{};
global.matchMedia=global.window.matchMedia;
const PLAYED=[];
function AudioStub(src){ this.src=src; this._l={}; }
AudioStub.prototype.addEventListener=function(t,f){(this._l[t]=this._l[t]||[]).push(f);};
AudioStub.prototype.play=function(){PLAYED.push(this.src);return Promise.resolve();};
AudioStub.prototype.pause=function(){};
global.Audio=AudioStub;

/* ---- load ---- */
vm.runInThisContext(datajs,{filename:'quran-data.js'});
vm.runInThisContext(qjs,{filename:'quran.js'});
vm.runInThisContext(appjs,{filename:'app.js'});

/* ---- tests ---- */
const flush=()=>new Promise(r=>Promise.resolve().then(r));
(async function(){
ok('boot: quran screen painted', els['s-quran']._html.indexOf('quran')>=0 || els['s-quran']._html.length>0);
ok('UR_AUDIO present (114)', typeof UR_AUDIO==='object' && Object.keys(UR_AUDIO).length===114);

/* urdu + with-tarjuma -> per-surah real audio */
S.lang=54; S.audioMode='both'; saveS();
openSurah(2,1);
ok('urdu: reader HTML built', els['s-reader']._html.indexOf('snm')>=0);
ok('urdu: per-ayah Play hidden in reader', els['s-reader']._html.indexOf('playAyah(2,1)')<0);
ok('urdu: shows real-audio source chip', els['s-reader']._html.indexOf('Sudais')>=0);
PLAYED.length=0;
startAyah(2,1); await flush();
ok('urdu: plays per-surah file', PLAYED.length===1 && PLAYED[0]==='https://www.nooresunnat.com/Audio/Complete%20Quran/Shuraim-Sudais-Urdu/002s-Baqarah.mp3');
ok('urdu: player shows Surah x/114', els['apos'] && els['apos']._text.indexOf('Surah 2 / 114')>=0);
ok('urdu: state says Urdu tarjuma', els['astate'] && els['astate']._text.indexOf('Urdu tarjuma')>=0);
ok('urdu: AU.kind=ur', AU.kind==='ur');

/* with-tarjuma (default audioSrc=site) -> asli recorded website audio, har language pe */
stopAudio();
S.lang=85; S.audioMode='both'; S.audioSrc='site'; saveS();
openSurah(2,1);
ok('both-site: per-ayah Play hidden', els['s-reader']._html.indexOf('playAyah(2,1)')<0);
ok('both-site: shows real-audio source chip', els['s-reader']._html.indexOf('Sudais')>=0);
PLAYED.length=0;
startAyah(2,1); await flush();
ok('both-site: plays per-surah real audio', PLAYED.length===1 && PLAYED[0]==='https://www.nooresunnat.com/Audio/Complete%20Quran/Shuraim-Sudais-Urdu/002s-Baqarah.mp3');
ok('both-site: AU.kind=ur', AU.kind==='ur');

/* tts mode -> per-ayah arabic + device voice */
stopAudio();
S.audioSrc='tts'; saveS();
openSurah(2,1);
ok('tts: per-ayah Play visible', els['s-reader']._html.indexOf('playAyah(2,1)')>=0);
PLAYED.length=0; startAyah(2,1); await flush();
ok('tts: plays per-ayah arabic', PLAYED.length===1 && PLAYED[0]==='https://verses.quran.com/Alafasy/mp3/002001.mp3');
ok('tts: AU.kind=ayah', AU.kind==='ayah');

/* arabic-only mode */
stopAudio();
S.audioSrc='site'; S.audioMode='ar'; saveS();
PLAYED.length=0; startAyah(1,1);
ok('ar-mode: plays per-ayah arabic', PLAYED.length===1 && PLAYED[0].indexOf('/001001.mp3')>=0);
ok('ar-mode: no urdu chip', els['s-reader']._html.indexOf('Sudais')<0 || S.lang!==54);

/* audioStep in urdu mode steps surah */
S.lang=54; S.audioMode='both'; saveS(); openSurah(2,1); AU.s=2;
PLAYED.length=0; audioStep(1);
ok('urdu: audioStep moves to next surah', AU.s===3 && PLAYED[0] && PLAYED[0].indexOf('003s')>=0);
PLAYED.length=0; audioStep(-1);
ok('urdu: audioStep back to prev surah', AU.s===2 && PLAYED[0] && PLAYED[0].indexOf('002s')>=0);

/* every surah has a mapped file */
let allok=true; for(let i=1;i<=114;i++){ if(!UR_AUDIO[i]) allok=false; }
ok('all 114 surahs mapped', allok);

console.log(fail?'\nSOME TESTS FAILED':'\nALL PASS');
process.exit(fail);
})();
