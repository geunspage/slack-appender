package kr.geun.logback.appender;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Layout;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import ch.qos.logback.core.status.ErrorStatus;

/**
 * Custom Logback Appender
 *  - Slack
 * 
 * @author geunspage
 *
 */
public class SlackAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {

	private final int TIME_OUT = 3000;
	private final String WEBHOOK_BASIC_URL = "https://hooks.slack.com/services/%s";
	private String webhook_token;
	private String channel;
	private Level noti_level;
	private Layout<ILoggingEvent> layout;

	private long lastSentTimestamp = 0;
	private final long sendInterval = 5000; // 5 sec

	private static Map<Level, String> LEVEL_COLOR = new HashMap<Level, String>();

	static {
		LEVEL_COLOR.put(Level.TRACE, "#8C8C8C");
		LEVEL_COLOR.put(Level.DEBUG, "#B2EBF4");
		LEVEL_COLOR.put(Level.INFO, "#0100FF");
		LEVEL_COLOR.put(Level.WARN, "#FF5E00");
		LEVEL_COLOR.put(Level.ERROR, "#FF0000");
	}

	@Override
	public void start() {
		int error_cnt = 0;

		if (webhook_token == null) {
			addStatus(new ErrorStatus("Requirement webhook_token, You have to set for the appdener named \"" + name + "\".", this));
			error_cnt++;
		}

		if (noti_level == null) {
			addStatus(new ErrorStatus("Requirement noti_level, You have to set for the appdener named \"" + name + "\".", this));
			error_cnt++;
		}

		if (channel == null) {
			addStatus(new ErrorStatus("Requirement channel, You have to set for the appdener named \"" + name + "\".", this));
			error_cnt++;
		}

		if (layout == null) {
			addStatus(new ErrorStatus("Requirement layout, You have to set for the appdener named \"" + name + "\".", this));
			error_cnt++;
		}
		if (error_cnt == 0) {
			super.start(); //Slack Appender start
		}
	}

	@Override
	protected void append(ILoggingEvent eventObject) {
		if (isStarted() == false) { //
			return;
		}

		try {
			if (eventObject.getLevel().isGreaterOrEqual(noti_level)) {
				long now = System.currentTimeMillis();

				if (lastSentTimestamp == 0 || (lastSentTimestamp + sendInterval < now)) {

					Map<String, String> param = new LinkedHashMap<String, String>();
					param.put("pretext", getPretext(eventObject));
					param.put("text", layout.doLayout(eventObject));
					param.put("channel", channel);
					param.put("color", LEVEL_COLOR.get(eventObject.getLevel()));

					requestPost(toJsonString(param).getBytes(StandardCharsets.UTF_8));

				} else {
					//TODO : add Queue
				}
			}
		} catch (Exception e) {
			addError("Slack Appender Exception", e);
			e.printStackTrace(System.err);
		} finally {

		}
	}

	/**
	 * Get pretext
	 * 
	 * @param eventObject
	 * @return
	 */
	private String getPretext(ILoggingEvent eventObject) {
		StringBuffer rtnSb = new StringBuffer("");
		rtnSb.append("[").append(eventObject.getLevel().levelStr).append("]").append(" ").append(eventObject.getMessage());
		return rtnSb.toString();
	}

	/**
	 * Http Request 
	 *  - POST
	 * 
	 * @param param
	 * @throws Exception
	 */
	private void requestPost(byte[] param) throws Exception {
		URL url = new URL(String.format(WEBHOOK_BASIC_URL, webhook_token));
		//TODO : reuse
		HttpURLConnection conn = (HttpURLConnection)url.openConnection();
		conn.setConnectTimeout(TIME_OUT);
		conn.setReadTimeout(TIME_OUT);
		conn.setDoOutput(true);
		conn.setRequestMethod("POST");
		conn.setFixedLengthStreamingMode(param.length);
		conn.setRequestProperty("Content-Type", "application/json");

		OutputStream os = conn.getOutputStream();

		os.write(param);
		os.flush();
		os.close();

		if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
			throw new Exception(conn.getResponseMessage() + "\n\n" + new String(param, StandardCharsets.UTF_8));
		}
	}

	/**
	 * Convert Map to Json String
	 * 
	 * @param map
	 * @return
	 */
	private String toJsonString(Map<String, String> map) {
		StringBuffer rtnSb = new StringBuffer("");
		if (map == null) {
			return rtnSb.toString();
		}
		boolean firstKey = true;
		Iterator<Entry<String, String>> iter = map.entrySet().iterator();

		rtnSb.append("{");

		while (iter.hasNext()) {
			if (firstKey) {
				firstKey = false;
			} else {
				rtnSb.append(",");
			}

			Entry<String, String> entry = iter.next();
			rtnSb.append(quoteStr(entry.getKey()));
			rtnSb.append(":");
			rtnSb.append(quoteStr(entry.getValue()));
		}
		rtnSb.append("}");

		return rtnSb.toString();
	}

	/**
	 * String in double quotation marks. 
	 * 
	 * @param str
	 * @return
	 */
	private String quoteStr(String str) {
		final String DOUBLE_QUOTE = "\"";
		return DOUBLE_QUOTE + str + DOUBLE_QUOTE;
	}

	/*Setter Methods*/

	public void setWebhook_token(String webhook_token) {
		this.webhook_token = webhook_token;
	}

	public void setNoti_level(Level noti_level) {
		this.noti_level = noti_level;
	}

	public void setChannel(String channel) {
		this.channel = channel;
	}

	public void setLayout(Layout<ILoggingEvent> layout) {
		this.layout = layout;
	}
}
