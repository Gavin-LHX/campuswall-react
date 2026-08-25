import React from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import App from './App.jsx'
import { UserProvider } from './contexts/UserContext.jsx'
import './styles.css'

const loadAnalytics = () => {
  const websiteId = String(import.meta.env.VITE_UMAMI_WEBSITE_ID || '').trim()
  if (!websiteId) return
  const script = document.createElement('script')
  script.defer = true
  script.src = 'https://cloud.umami.is/script.js'
  script.dataset.websiteId = websiteId
  document.head.append(script)
}

createRoot(document.getElementById('app')).render(
  <React.StrictMode>
    <BrowserRouter>
      <UserProvider>
        <App />
      </UserProvider>
    </BrowserRouter>
  </React.StrictMode>
)

if (import.meta.env.PROD && import.meta.env.VITE_UMAMI_WEBSITE_ID) {
  if ('requestIdleCallback' in window) {
    window.requestIdleCallback(loadAnalytics, { timeout: 5000 })
  } else {
    window.setTimeout(loadAnalytics, 3000)
  }
}
