import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'

const html = readFileSync(new URL('../public/native-captcha.html', import.meta.url), 'utf8')
const script = html.match(/<script>([\s\S]*?)<\/script>/)[1]
const state = 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee'
async function bridge(query) {
  const nodes = { status: { textContent: '' }, return: { hidden: true, removeAttribute(k) { delete this[k] } } }
  let options
  let fetched = false
  const context = {
    URLSearchParams,
    location: { search: query },
    document: {
      getElementById: id => nodes[id],
      createElement: () => ({}),
      head: { appendChild: script => script.onload() }
    },
    window: { turnstile: { render: (target, value) => { options = value } } },
    fetch: async () => {
      fetched = true
      return { ok: true, json: async () => ({ captcha: { enabled: true, provider: 'turnstile', site_key: 'public-site-key' } }) }
    }
  }
  await vm.runInNewContext(script, context)
  return { nodes, options, fetched }
}
test('native challenge preserves action and binds return to caller state', async () => {
  const { nodes, options } = await bridge(`?state=${state}&action=register`)
  assert.equal(options.action, 'register')
  assert.equal(nodes.return.hidden, true)
  options.callback('token+with/special&characters')
  const result = new URL(nodes.return.href)
  assert.equal(result.protocol, 'campuswall:')
  assert.equal(result.hostname, 'captcha')
  assert.equal(result.searchParams.get('state'), state)
  assert.equal(result.searchParams.get('token'), 'token+with/special&characters')
  assert.equal(nodes.return.hidden, false)
  options['expired-callback']()
  assert.equal(nodes.return.hidden, true)
  assert.equal(nodes.return.href, undefined)
})
test('untrusted actions and malformed state never start a challenge', async () => {
  for(const query of [`?state=${state}&action=delete`, '?state=bad&action=login']) {
    const { fetched, nodes } = await bridge(query)
    assert.equal(fetched, false)
    assert.equal(nodes.return.hidden, true)
    assert.match(nodes.status.textContent, /App/)
  }
})
