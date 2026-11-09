// Exercise the public landing page in Chrome with mocked or real collectors.
import assert from 'node:assert/strict';
import { pathToFileURL } from 'node:url';

const [base, playwrightEntry] = process.argv.slice(2);
const live = base === 'https://gethandyai.app';
const realGoogleTag = process.argv.includes('--real-google-tag');
assert(live || new URL(base).hostname === '127.0.0.1');
const { chromium } = await import(pathToFileURL(playwrightEntry).href);
const browser = await chromium.launch({ executablePath: '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome' });
const signal = 'qa-private-sentinel';
const provider = url => {
    const { hostname, pathname } = new URL(url);
    if (hostname === 'api.mixpanel.com' && pathname === '/track') return 'mixpanel';
    if ((hostname.endsWith('google-analytics.com') || hostname === 'analytics.google.com') && pathname.endsWith('/collect')) return 'google';
    if (hostname === 'betterloopai.com' && pathname === '/api/ingest') return 'betterloop';
    return '';
};
const sdk = `window.Betterloop={init(o){if(this.started)return;this.started=true;fetch(o.endpoint,{method:'POST',body:JSON.stringify({path:location.pathname})});},shutdown(){this.started=false;}};`;
async function check(width, gpc = false) {
    const context = await browser.newContext({ viewport: { width, height: 820 } });
    const page = await context.newPage(), requests = [], errors = [];
    page.on('pageerror', error => errors.push(error.message));
    page.on('request', req => {
        const name = provider(req.url());
        if (name) requests.push({ name, url: req.url(), body: req.postData() ?? '' });
    });
    if (gpc) await context.addInitScript(() => Object.defineProperty(navigator, 'globalPrivacyControl', { get: () => true }));
    if (!live) await context.route('**/*', async route => {
        const url = new URL(route.request().url());
        if (url.origin === base) return route.continue();
        if (url.hostname === 'www.googletagmanager.com') return realGoogleTag ? route.continue() : route.fulfill({ contentType: 'text/javascript', body: 'window.qaGoogleLoaded=true' });
        if (url.pathname === '/sdk.js' && url.hostname === 'betterloopai.com') return route.fulfill({ contentType: 'text/javascript', body: sdk });
        if (provider(url.href)) return route.fulfill({ headers: { 'Access-Control-Allow-Origin': '*' }, body: '1' });
        return route.abort();
    });
    await page.goto(`${base}/?${signal}=1#${signal}`, { waitUntil: 'load', referer: `${base}/setup.html?${signal}=referrer` });
    assert.equal(requests.length, 0);
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
    assert.equal(await page.getByRole('link', { name: 'Contact support' }).getAttribute('href'), 'mailto:hi@gethandyai.app');
    const opener = page.getByRole('button', { name: 'Analytics choices' });
    assert(await opener.evaluate(el => Boolean(el.closest('[data-betterloop-ignore]'))));
    await opener.click();
    const dialog = page.getByRole('dialog', { name: 'Optional website analytics' });
    assert.equal(await dialog.getAttribute('data-betterloop-ignore'), '');
    const off = dialog.getByRole('button', { name: 'Keep off' });
    assert(await off.evaluate(el => el === document.activeElement));
    assert(await dialog.evaluate(el => el.scrollWidth <= innerWidth));
    const receipts = gpc ? [] : (live || realGoogleTag ? ['google', 'mixpanel', 'betterloop'] : ['mixpanel', 'betterloop']).map(name =>
        page.waitForResponse(response => provider(response.url()) === name && response.ok(), { timeout: 20000 }));
    const all = Promise.all(receipts);
    all.catch(() => {});
    await dialog.getByRole('button', { name: 'Allow analytics' }).click();
    if (gpc) {
        assert.equal(requests.length, 0);
        assert.equal(await page.evaluate(() => Boolean(localStorage.getItem('handyai.analytics.v1.device'))), false);
        await context.close();
        return;
    }
    const received = await all;
    if (live) {
        assert.equal((await received[1].text()).trim(), '1');
        assert.equal((await received[2].json()).ok, true);
    } else if (!realGoogleTag) {
        assert.equal(await page.evaluate(() => Object.prototype.toString.call(window.dataLayer.at(-1))), '[object Arguments]');
    }
    assert(requests.every(item => !decodeURIComponent(item.url + item.body).includes(signal)));
    const googleRequests = requests.filter(item => item.name === 'google');
    if (live || realGoogleTag) assert.equal(googleRequests.filter(item => new URL(item.url).searchParams.get('en') === 'page_view').length, 1);
    for (const request of googleRequests) {
        const fields = new URL(request.url).searchParams;
        assert.equal(fields.get('dl'), base + '/');
        assert.equal(fields.get('dr') ?? '', '');
    }
    await opener.click();
    await off.click();
    assert(await opener.evaluate(el => el === document.activeElement));
    assert.equal(await page.evaluate(() => localStorage.getItem('handyai.analytics.v1.device')), null);
    const count = requests.length;
    await page.reload({ waitUntil: 'load' });
    await page.goto(base + '/setup.html', { waitUntil: 'domcontentloaded' });
    assert.equal(requests.length, count);
    assert.deepEqual(errors, []);
    console.log(`${width}px: support link, default off, consent, ${received.map(response => provider(response.url()) + ' ' + response.status()).join(', ')}, safe payload, withdrawal and setup exclusion`);
    await context.close();
}
try {
    await check(375);
    await check(1440);
    await check(375, true);
    console.log('Global Privacy Control keeps analytics off.');
} finally {
    await browser.close();
}
