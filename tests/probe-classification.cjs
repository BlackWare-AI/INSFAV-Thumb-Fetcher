const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs'),vm=require('node:vm');
const source=fs.readFileSync('app/src/main/assets/profile-probe.js','utf8');
const PHOTO='https://scontent.example.cdninstagram.com/v/avatar.jpg';
function image(){return {src:PHOTO,currentSrc:PHOTO,complete:true,naturalWidth:320,naturalHeight:320,
    getAttribute:key=>key==='alt'?"example's profile picture":null,
    closest:selector=>selector==='article,nav,aside'?null:{}};}
function probe({text='',images=[],canonical='https://www.instagram.com/example/',path='/example/',high=false}={}) {
    const context={URL,location:{href:'https://www.instagram.com'+path,pathname:path},
        document:{body:{innerText:text},images,scripts:[],querySelector:()=>canonical?{href:canonical}:null,querySelectorAll:()=>[]},Image:class{},window:{}};
    return JSON.parse(vm.runInNewContext('('+source+')("example","123",'+high+')',context));
}
test('Turkish unavailable page text returns an immediate confirmed wall before canonical checks',()=>{
    const result=probe({text:'Bu sayfaya ulaşılamıyor',canonical:'https://www.instagram.com/other/'});
    assert.equal(result.wall,'PROFILE_PAGE_UNAVAILABLE');assert.equal(result.reason,'TEXT_MATCH');
    assert.equal(result.unavailableText,true);assert.equal(result.pageReady,true);assert.equal(result.searchCompleted,true);
});
test('English unavailable page text returns an immediate confirmed wall',()=>{
    const result=probe({text:"Sorry, this page isn't available"});
    assert.equal(result.wall,'PROFILE_PAGE_UNAVAILABLE');assert.equal(result.reason,'TEXT_MATCH');
    assert.equal(result.unavailableText,true);assert.equal(result.pageReady,true);assert.equal(result.searchCompleted,true);
});
test('all required Turkish unavailable phrases match in lowercase page text',()=>{
    for(const phrase of ['bu sayfa kullanılamıyor','sayfa kaldırılmış','bağlantı bozuk olabilir'])
        assert.equal(probe({text:'uyarı: '+phrase}).wall,'PROFILE_PAGE_UNAVAILABLE');
});
test('a visible account-bound image candidate continues through normal profile processing',()=>{
    const result=probe({text:'Bu sayfa kullanılamıyor',images:[image()]});
    assert.equal(result.wall,undefined);assert.equal(result.candidateFound,true);
    assert.equal(result.unavailableText,true);assert.equal(result.url,PHOTO);
});
test('challenge and login gates remain ahead of unavailable page text',()=>{
    const context={URL,location:{href:'https://www.instagram.com/challenge/',pathname:'/challenge/'},
        document:{body:{innerText:'bu sayfa kullanılamıyor'},images:[],scripts:[],querySelector:()=>null,querySelectorAll:()=>[]},Image:class{},window:{}};
    const challenge=JSON.parse(vm.runInNewContext('('+source+')("example","123",false)',context));
    assert.equal(challenge.wall,'CHALLENGE_REQUIRED');
});
test('unavailable message with an account image candidate does not become a closed-page result in either resolution mode',()=>{
    for(const high of [false,true]) {
        const result=probe({text:'bu sayfa kullanılamıyor',images:[image()],high});
        assert.notEqual(result.wall,'PROFILE_PAGE_UNAVAILABLE');assert.equal(result.candidateFound,true);
    }
});
