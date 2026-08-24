import { useEffect, useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import dayjs from 'dayjs'
import relativeTime from 'dayjs/plugin/relativeTime'
import 'dayjs/locale/zh-cn'
import api from '../services/api'
import Modal from '../components/Modal.jsx'
import SafeHtml from '../components/SafeHtml.jsx'
import { useAlert } from '../contexts/AlertContext.jsx'
import { useUser } from '../contexts/UserContext.jsx'
import { usePlatform } from '../contexts/PlatformContext.jsx'

dayjs.extend(relativeTime)
dayjs.locale('zh-cn')

const sampleTags = ['日常', '树洞', '表白', '学习', '寻物', '吐槽']

export default function Home() {
  const [runTime, setRunTime] = useState({ days: 0, hours: 0, minutes: 0, seconds: 0 })
  const [hotMessages, setHotMessages] = useState([])
  const [loading, setLoading] = useState(true)
  const [quickText, setQuickText] = useState('')
  const [quickTag, setQuickTag] = useState('')
  const [noticeContent, setNoticeContent] = useState('')
  const [noticeOpen, setNoticeOpen] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const alert = useAlert()
  const { user } = useUser()
  const { community } = usePlatform()
  const navigate = useNavigate()
  const canPublish = !user?.is_muted
    && community.posting_enabled
    && (Boolean(user) || community.guest_posting_enabled)
  const publishDisabledReason = user?.is_muted
    ? (user.mute_reason ? `账号已被禁言：${user.mute_reason}` : '账号已被禁言，暂时不能发帖')
    : (!community.posting_enabled
        ? (community.pause_reason || '管理员暂时关闭了发帖功能')
        : '当前仅登录学生可以发帖')

  const startDate = useMemo(() => new Date(2025, 7, 21, 13, 37, 11), [])

  useEffect(() => {
    const update = () => {
      const diff = Date.now() - startDate.getTime()
      setRunTime({
        days: Math.floor(diff / 86400000),
        hours: Math.floor((diff % 86400000) / 3600000),
        minutes: Math.floor((diff % 3600000) / 60000),
        seconds: Math.floor((diff % 60000) / 1000)
      })
    }
    update()
    const timer = window.setInterval(update, 1000)
    return () => window.clearInterval(timer)
  }, [startDate])

  const loadHotMessages = async () => {
    setLoading(true)
    try {
      const response = await api.getHotMessages()
      if (response.data?.success) setHotMessages(response.data.messages || [])
    } catch (error) {
      alert.showTopRightAlert(error.message, 'warning', '加载热门失败')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadHotMessages()
    api.getNotice().then((response) => {
      if (response.data?.success) {
        const content = Array.isArray(response.data.content)
          ? response.data.content.map((item) => item.content || item.text || '').filter(Boolean).join('<hr />')
          : response.data.content
        setNoticeContent(content || '')
        const month = `${new Date().getFullYear()}-${new Date().getMonth() + 1}`
        if (content && !localStorage.getItem(`hasVisitedWall${month}`)) {
          setNoticeOpen(true)
          localStorage.setItem(`hasVisitedWall${month}`, 'true')
        }
      }
    }).catch(() => {})
  }, [])

  const submitQuick = async (event) => {
    event.preventDefault()
    if (!canPublish) {
      alert.showTopRightAlert(publishDisabledReason, 'warning', '暂时无法发布')
      return
    }
    if (!quickText.trim()) {
      alert.showTopRightAlert('请输入留言内容', 'warning', '提示')
      return
    }
    setSubmitting(true)
    try {
      const response = await api.submitMessage({ text: quickText.trim(), tags: quickTag, filenames: [] })
      setQuickText('')
      setQuickTag('')
      const pendingReview = response.data?.moderation_status === 'pending'
      alert.showTopRightAlert(
        pendingReview ? '留言已提交审核，可在个人中心查看进度' : '发布成功！已同步至校园墙',
        'success',
        pendingReview ? '等待审核' : '成功'
      )
      loadHotMessages()
    } catch (error) {
      alert.showTopRightAlert(error.message, 'warning', '发布失败')
    } finally {
      setSubmitting(false)
    }
  }

  const triggerPublishModal = () => {
    if (!canPublish) {
      alert.showTopRightAlert(publishDisabledReason, 'warning', '暂时无法发布')
      return
    }
    navigate('/wall')
    window.setTimeout(() => window.dispatchEvent(new Event('open-publish-modal')), 80)
  }

  return (
    <div className="space-y-14">
      <section className="hero-section px-6 py-12 text-left md:px-12 md:py-16">
        <div className="hero-content mx-auto max-w-3xl">
          <p className="text-[0.72rem] font-semibold tracking-[0.22em] text-[var(--text-muted)]">CAMPUS WALL</p>
          <h1 className="display-title mt-3 text-4xl text-[var(--text-primary)] md:text-6xl">
            把想说的话，<br className="hidden sm:block" />贴在墙上。
          </h1>
          <p className="hero-subtitle mt-4 max-w-xl text-sm md:text-base">
            匿名倾诉、同学互助。这里没有围观压力，只有被听见的可能。
          </p>

          <div className="mt-8 flex flex-wrap items-center gap-3">
            <Link to="/wall" className="btn btn-lg btn-primary px-6">
              <span>去墙上看看</span>
            </Link>
            <button
              type="button"
              className="btn btn-lg btn-outline px-6"
              onClick={triggerPublishModal}
              disabled={!canPublish}
              title={canPublish ? '写一条' : publishDisabledReason}
            >
              <span>写一条</span>
            </button>
            <span className="runtime-pill inline-flex items-center gap-1 rounded-full px-3 py-1.5 text-xs">
              已运行 <b>{runTime.days}</b> 天
            </span>
          </div>

          <div className="hero-bubbles mt-10">
            <div className="hero-bubble">
              <strong>默认可匿名</strong>
              <span>学号只用于认证，公开页不会出现真实身份。</span>
            </div>
            <div className="hero-bubble">
              <strong>图文都能贴</strong>
              <span>支持图片、音频和短视频，把校园瞬间留下来。</span>
            </div>
            <div className="hero-bubble">
              <strong>同学之间互助</strong>
              <span>寻物、提问、吐槽、表白，都有人接住。</span>
            </div>
          </div>
        </div>
      </section>

      <section>
        <div className="mb-6">
          <h2 className="section-title text-2xl md:text-3xl text-[var(--text-primary)]">墙上常见的事</h2>
          <p className="mt-1.5 text-sm text-[var(--text-secondary)]">发得快，回得也快。匿名、图文、互助都在同一面墙。</p>
        </div>
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {[
            ['即刻能发', '打开就能写。短句、长文、一张图，都可以贴上去。'],
            ['有来有回', '点赞、评论、盖楼。说出来的话，会有人接住。'],
            ['不只是文字', '图片、音频、短视频都能带上，把现场留下来。'],
            ['有人值守', '内容会审核和管理，尽量把墙留成干净的公共空间。']
          ].map(([title, text]) => (
            <div key={title} className="card feature-card p-5 space-y-2">
              <h3 className="text-base font-semibold text-[var(--text-primary)]">{title}</h3>
              <p className="text-sm text-[var(--text-secondary)] leading-relaxed">{text}</p>
            </div>
          ))}
        </div>
      </section>

      <section className="mx-auto max-w-3xl">
        <div className="mb-5">
          <h2 className="section-title text-2xl md:text-3xl text-[var(--text-primary)]">先写一句</h2>
          <p className="mt-1.5 text-sm text-[var(--text-secondary)]">默认匿名。想得完整再发，也可以先写在这里。</p>
        </div>
        <form className="card composer-card p-5 md:p-6" onSubmit={submitQuick}>
          {!canPublish ? (
            <div className="info-callout status-warning mb-4">
              <i className="bi bi-info-circle-fill" />
              <span>{publishDisabledReason}</span>
              {!user && community.posting_enabled ? <Link className="ml-auto font-bold" to="/login">前往登录</Link> : null}
            </div>
          ) : null}
          <textarea
            className="field min-h-24 w-full border-0 bg-transparent focus:ring-0 p-0 text-sm md:text-base outline-none resize-none"
            value={quickText}
            onChange={(event) => setQuickText(event.target.value)}
            placeholder="此刻有什么想和大家分享的？（默认匿名发布）"
            maxLength={1000}
            disabled={!canPublish}
          />
          <div className="mt-4 flex flex-wrap items-center justify-between gap-3 border-t border-[var(--border-color)] pt-3.5">
            <div className="flex flex-wrap items-center gap-1.5">
              <span className="text-xs font-semibold text-[var(--text-muted)] mr-1">快捷标签:</span>
              {sampleTags.map((tag) => (
                <button
                  key={tag}
                  type="button"
                  disabled={!canPublish}
                  onClick={() => setQuickTag(quickTag === tag ? '' : tag)}
                  className={`badge text-xs cursor-pointer ${quickTag === tag ? 'bg-[var(--primary-color)] text-white border-transparent' : ''}`}
                >
                  #{tag}
                </button>
              ))}
            </div>
            <button
              className="btn btn-sm btn-primary ml-auto px-4"
              type="submit"
              disabled={!canPublish || submitting || !quickText.trim()}
            >
              <i className="bi bi-send-fill" />
              <span>{submitting ? '发送中...' : '立即发布'}</span>
            </button>
          </div>
        </form>
      </section>

      <section>
        <div className="mb-6 flex items-end justify-between gap-4">
          <div>
            <h2 className="section-title text-2xl md:text-3xl text-[var(--text-primary)]">最近被看见的</h2>
            <p className="mt-1.5 text-sm text-[var(--text-secondary)]">墙上正在被讨论的几条。</p>
          </div>
          <Link to="/wall" className="text-sm font-semibold text-[var(--primary-color)]">全部动态</Link>
        </div>

        {loading ? (
          <div className="grid gap-5 md:grid-cols-3">
            {[1, 2, 3].map((i) => (
              <div key={i} className="card p-5 space-y-3">
                <div className="skeleton h-4 w-1/3" />
                <div className="skeleton h-14 w-full" />
                <div className="skeleton h-4 w-1/2" />
              </div>
            ))}
          </div>
        ) : null}

        {!loading && hotMessages.length === 0 ? (
          <div className="empty-state-card">
            <i className="bi bi-inbox" />
            <p className="mt-3 font-semibold">暂无热门留言</p>
            <p className="text-xs text-[var(--text-muted)]">快去发第一条有趣的留言吧！</p>
          </div>
        ) : null}

        <div className="grid gap-5 md:grid-cols-3">
          {hotMessages.map((message, index) => (
            <Link
              key={message.id}
              to={`/wall/message/${message.id}`}
              className="card hot-message-card p-5 flex flex-col justify-between group"
            >
              <div className="space-y-2.5">
                <div className="flex items-center justify-between">
                  <span className="font-[family-name:var(--font-display)] text-sm text-[var(--text-muted)]">
                    {String(index + 1).padStart(2, '0')}
                  </span>
                  <span className="text-xs text-[var(--text-muted)]">
                    {message.timestamp ? dayjs(message.timestamp).fromNow() : ''}
                  </span>
                </div>
                {message.pinned || message.featured || message.poll ? (
                  <div className="flex flex-wrap gap-1.5">
                    {message.pinned ? <span className="badge status-warning text-[10px]"><i className="bi bi-pin-angle" />置顶</span> : null}
                    {message.featured ? <span className="badge status-success text-[10px]"><i className="bi bi-star-fill" />精华</span> : null}
                    {message.poll ? <span className="badge text-[10px]"><i className="bi bi-ui-radios-grid" />投票</span> : null}
                  </div>
                ) : null}
                <p className="message-text line-clamp-3 text-sm text-[var(--text-primary)] leading-relaxed group-hover:text-[var(--primary-color)] transition-colors">
                  {message.text || message.poll?.question || '校园墙留言'}
                </p>
              </div>

              <div className="hot-message-meta mt-4 pt-3 border-t border-[var(--border-color)] flex items-center justify-between">
                <span className="text-xs font-semibold text-[var(--text-secondary)]">
                  {message.anonymous !== false
                    ? '匿名同学'
                    : (message.display_name_snapshot || '同学')}
                </span>
                <span className="flex items-center gap-3 text-xs">
                  <span className="flex items-center gap-1 text-rose-500">
                    <i className="bi bi-hand-thumbs-up-fill" /> {message.likes || 0}
                  </span>
                  <span className="flex items-center gap-1 text-[var(--primary-color)]">
                    <i className="bi bi-chat-dots-fill" /> {message.comments?.length || 0}
                  </span>
                </span>
              </div>
            </Link>
          ))}
        </div>
      </section>

      <section className="card p-8 md:p-10">
        <div className="mx-auto max-w-2xl space-y-3">
          <h2 className="section-title text-xl md:text-2xl text-[var(--text-primary)]">关于这面墙</h2>
          <p className="text-sm text-[var(--text-secondary)] leading-relaxed">
            由学生搭建和维护，给同学一个平等说话的地方。不是学校官方站点。欢迎反馈，一起把它留干净。
          </p>
          <div className="flex flex-wrap justify-center gap-3 pt-2">
            <a
              className="btn btn-sm btn-outline"
              href="https://github.com/Gavin-LHX/campuswall-react"
              target="_blank"
              rel="noreferrer"
            >
              <i className="bi bi-github" />
              <span>开源代码仓库</span>
            </a>
            <Link to="/rules" className="btn btn-sm btn-outline">
              <i className="bi bi-file-earmark-ruled" />
              <span>社区公约</span>
            </Link>
            <Link to="/help" className="btn btn-sm btn-outline">
              <i className="bi bi-envelope" />
              <span>联系站长 / 帮助</span>
            </Link>
          </div>
        </div>
      </section>

      {/* System Announcement Modal */}
      <Modal
        visible={noticeOpen}
        title="校园墙公告"
        onClose={() => setNoticeOpen(false)}
        footer={
          <button className="btn btn-primary" onClick={() => setNoticeOpen(false)}>
            我知道了
          </button>
        }
      >
        <SafeHtml html={noticeContent || '暂无公告'} />
      </Modal>
    </div>
  )
}
