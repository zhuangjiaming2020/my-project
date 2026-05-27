const TOKEN_KEY = 'mall_token'
const USER_KEY  = 'mall_user'

export function getToken()  { return localStorage.getItem(TOKEN_KEY) }
export function setToken(t) { localStorage.setItem(TOKEN_KEY, t) }
export function removeToken() { localStorage.removeItem(TOKEN_KEY) }

export function getUser()   { try { return JSON.parse(localStorage.getItem(USER_KEY)) } catch { return null } }
export function setUser(u)  { localStorage.setItem(USER_KEY, JSON.stringify(u)) }
export function removeUser(){ localStorage.removeItem(USER_KEY) }

export function isLoggedIn() { return !!getToken() }

export function logout() {
  removeToken()
  removeUser()
}
