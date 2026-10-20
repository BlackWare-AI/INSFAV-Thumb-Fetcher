const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs'), vm=require('node:vm');
const source=fs.readFileSync('app/src/main/assets/profile-probe.js','utf8');
const PHOTO='https://scontent.example.cdninstagram.com/v/avatar.jpg';
function image(alt,src=PHOTO, options={}) {
    return {src,currentSrc:src,complete:true,naturalWidth:320,naturalHeight:320,
        getAttribute:key=>key==='alt'?alt:null,
        closest:selector=>selector==='article,nav,aside'?(options.post?{}:null):(options.header===false?null:{}),
        ...options};
}
function probe({images=[],data=[],path='/example/',canonical='https://www.instagram.com/example/',forms=[],createdLoaded=true}={}) {
    const context={URL, location:{href:'https://www.instagram.com'+path,pathname:path},
        document:{images,scripts:data.map(x=>({textContent:typeof x==='string'?x:JSON.stringify(x)})),
            querySelector:()=>canonical?{href:canonical}:null,querySelectorAll:()=>forms},
        Image:class {constructor(){this.complete=createdLoaded;this.naturalWidth=createdLoaded?320:0;this.naturalHeight=createdLoaded?320:0;}},
        window:{}};
    return JSON.parse(vm.runInNewContext('('+source+')("example","123")',context));
}
test('loaded profile header avatar is accepted with its exact discovered URL',()=>{
    const x=probe({images:[image("example's profile picture")]});
    assert.equal(x.url,PHOTO);assert.equal(x.loaded,true);assert.equal(x.kind,'profile_header');
});
test('page completion without a decoded avatar does not succeed',()=>{
    assert.equal(probe({images:[image("example's profile picture",PHOTO,{complete:false})]}).url,undefined);
    assert.equal(probe({images:[image("example's profile picture",PHOTO,{naturalWidth:0})]}).url,undefined);
});
test('post and navigation images never supply a profile photo',()=>{
    assert.equal(probe({images:[image("example's profile picture",PHOTO,{post:true})]}).url,undefined);
    assert.equal(probe({images:[image('Photo by example on October 20')]}).url,undefined);
    assert.equal(probe({images:[image("example's profile picture",PHOTO,{header:false})]}).url,undefined);
});
test('similar account names and stale canonical route are rejected',()=>{
    assert.equal(probe({images:[image("example2's profile picture")]}).url,undefined);
    assert.equal(probe({images:[image("other's profile picture")]}).url,undefined);
    assert.equal(probe({canonical:'https://www.instagram.com/other/',images:[image("example's profile picture")]}).url,undefined);
});
test('unrelated JSON image URLs and og-image style objects cannot succeed',()=>{
    assert.equal(probe({data:[{image:PHOTO},{username:'other',id:'4',profile_pic_url:PHOTO}]}).url,undefined);
});
test('identity-bound JSON candidate is loaded separately before success',()=>{
    const data=[{user:{username:'example',id:'4',profile_pic_url:PHOTO}}];
    assert.equal(probe({data,createdLoaded:false}).url,undefined);
    const x=probe({data});assert.equal(x.url,PHOTO);assert.equal(x.kind,'profile_json');
});
test('default avatar and foreign image host are rejected',()=>{
    assert.equal(probe({data:[{username:'example',id:'4',has_anonymous_profile_picture:true}],images:[image("example's profile picture")]}).wall,'PROFILE_DEFAULT_AVATAR');
    assert.equal(probe({images:[image("example's profile picture",'https://evil.test/avatar.jpg')]}).url,undefined);
});
test('two conflicting header photos are ambiguous',()=>{
    assert.equal(probe({images:[image("example's profile picture"),image("example's profile picture",PHOTO+'?other=1')]}).wall,'PROFILE_DATA_AMBIGUOUS');
});
test('visible login forms and challenge routes stop',()=>{
    assert.equal(probe({forms:[{id:'loginForm',getClientRects:()=>[{}],getAttribute:()=>''}]}).wall,'COOKIE_INVALID_EXPIRED');
    assert.equal(probe({path:'/challenge/'}).wall,'CHALLENGE_REQUIRED');
});
test('generic challenge module names and hidden forms are not walls',()=>{
    assert.equal(probe({data:[{modules:['captcha','PolarisErrorRoot.react']}],forms:[{id:'loginForm',getClientRects:()=>[]}]}).wall,undefined);
});
test('routed error is not reported as a nonexistent account',()=>{
    assert.equal(probe({data:[{route:{rootView:{resource:{__dr:'PolarisErrorRoot.react'}}}}]}).wall,'PROFILE_PAGE_UNAVAILABLE');
});
test('viewer proof must match server-returned ds_user_id',()=>{
    assert.equal(probe({data:[{viewerId:'123'}]}).authenticated,true);
    assert.equal(probe({data:[{viewerId:'321'}]}).authenticated,false);
    assert.equal(probe({data:[{current_user:{username:'viewer',id:'123'}}]}).authenticated,true);
});
