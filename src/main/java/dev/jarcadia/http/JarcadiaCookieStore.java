package dev.jarcadia.http;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.http.client.CookieStore;
import org.apache.http.cookie.Cookie;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.jarcadia.redao.ProxyIndex;
import dev.jarcadia.redao.RedaoCommando;

class JarcadiaCookieStore implements CookieStore {
	
    private final Logger logger = LoggerFactory.getLogger(JarcadiaCookieStore.class);
	
	private final ProxyIndex<JarcadiaCookie> cookieSet;
	
	protected JarcadiaCookieStore(RedaoCommando rcommando, String id) {
		this.cookieSet = rcommando.getPrimaryIndex("cookies." + id, JarcadiaCookie.class);
	}

	@Override
	public void addCookie(Cookie source) {
//		logger.info("Adding cookie {}={} version {} ", source.getName(), source.getValue(), source.getVersion());
		JarcadiaCookie cookie = cookieSet.get(getId(source));
		cookie.setValues(source.getDomain(), source.getPath(), source.getName(), source.getValue(),
				source.getVersion(), source.isPersistent(), source.isSecure());
	}

	@Override
	public List<Cookie> getCookies() {
		return cookieSet.stream().collect(Collectors.toList());
	}

	@Override
	public boolean clearExpired(Date date) {
		return cookieSet.stream()
            .filter(c -> c.isExpired(date))
            .map(c -> c.delete())
            .reduce(false, (anyPurged, purged) -> anyPurged || purged);
	}

	@Override
	public void clear() {
		cookieSet.stream().forEach(c -> c.delete());
	}
	
	private String getId(Cookie cookie) {
		return normalizeDomain(cookie.getDomain()) + normalizePath(cookie.getPath()) + ":" + cookie.getName();
	}
	
	private String normalizeDomain(String domain) {
		return domain == null ? "" : domain.indexOf(".") == -1 ? domain.toLowerCase() + ".local" : domain.toLowerCase();
	}
	
	private String normalizePath(String path) {
		return path == null ? "/" : path;
	}
}
