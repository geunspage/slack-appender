package kr.geun.logback.appender;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.concurrent.locks.ReentrantLock;

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

	private final long SEND_INTERVAL = 5000; // 5 sec
	private final int TIME_OUT = 3000;
	private Queue<ILoggingEvent> LINKED_QUE = new LinkedList<ILoggingEvent>();
	private final int MAX_SEND_SIZE = 30;
	private long LAST_SENT_TIMESTAMP = 0;
	private static boolean IS_SEC_THREAD_RUN = false;
	protected final ReentrantLock LOCK = new ReentrantLock(true);
	private static Map<Level, String> LEVEL_COLOR = new HashMap<Level, String>();

	private String webhook_url; /* required */
	private String channel; /* required */
	private Level noti_level; /* required */
	private Layout<ILoggingEvent> layout; /* required */

	@Override
	public void start() {
		int error_cnt = 0;

		if (webhook_url == null) {
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
			setLevelColor();
			super.start(); //Slack Appender start
		}
	}

	/**
	 * Setting Level Color
	 */
	private void setLevelColor() {
		LEVEL_COLOR.put(Level.TRACE, "#8C8C8C");
		LEVEL_COLOR.put(Level.DEBUG, "#B2EBF4");
		LEVEL_COLOR.put(Level.INFO, "#0100FF");
		LEVEL_COLOR.put(Level.WARN, "#FF5E00");
		LEVEL_COLOR.put(Level.ERROR, "#FF0000");
	}

	@Override
	protected void append(ILoggingEvent eventObject) {
		if (isStarted() == false) {
			return;
		}

		LOCK.lock();
		try {
			if (eventObject.getLevel().isGreaterOrEqual(noti_level)) {
				long now = System.currentTimeMillis();
				if (LAST_SENT_TIMESTAMP == 0 || (LAST_SENT_TIMESTAMP + SEND_INTERVAL < now)) {
					new Thread(new SenderRunner(eventObject)).start();

				} else {
					LINKED_QUE.offer(eventObject);
					if (IS_SEC_THREAD_RUN == false) {
						IS_SEC_THREAD_RUN = true;
						new Thread(new MultiSenderRunner()).start();
					}

				}

				LAST_SENT_TIMESTAMP = now;
			}
		} catch (Exception e) {
			addError("Slack Appender Exception", e);
			e.printStackTrace(System.err);
		} finally {
			LOCK.unlock();
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
		URL url = new URL(webhook_url);
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
			rtnSb.append(coverQuoteStr(entry.getKey()));
			rtnSb.append(":");
			rtnSb.append(coverQuoteStr(entry.getValue()));
		}
		rtnSb.append("}");

		return rtnSb.toString();
	}

	/**
	 * Convert List to Json Array
	 *  - Slack Api Format
	 * 
	 * @param list
	 * @return
	 */
	private String toJsonArray(List<String> list) {
		StringBuffer rtnSb = new StringBuffer("");
		if (list == null) {
			return rtnSb.toString();
		}

		boolean firstKey = true;
		rtnSb.append("{\"attachments\":[");
		for (String str : list) {
			if (firstKey) {
				firstKey = false;
			} else {
				rtnSb.append(",");
			}
			rtnSb.append(str);
		}

		rtnSb.append("]}");
		return rtnSb.toString();
	}

	/**
	 * String in double quotation marks. 
	 * 
	 * @param str
	 * @return
	 */
	private String coverQuoteStr(String str) {
		final String DOUBLE_QUOTE = "\"";
		return DOUBLE_QUOTE + str + DOUBLE_QUOTE;
	}

	/**
	 * replace str
	 *  - " -> \" 
	 * 
	 * @param str
	 * @return
	 */
	private String replaceQuoteStr(String str) {
		if (str == null) {
			return "";
		}
		return str.replace("\"", "\\\"");
	}

	/**
	 * Send message 
	 * 
	 * @param eventObject
	 * @return
	 */
	private String makeSendText(ILoggingEvent eventObject) {
		Map<String, String> param = new LinkedHashMap<String, String>();
		param.put("pretext", replaceQuoteStr(getPretext(eventObject)));
		param.put("text", replaceQuoteStr(layout.doLayout(eventObject)));
		param.put("channel", channel);
		param.put("color", LEVEL_COLOR.get(eventObject.getLevel()));

		return toJsonString(param);
	}

	/**
	 * Single Sender
	 * 
	 * @author geunspage
	 *
	 */
	private class SenderRunner implements Runnable {
		private ILoggingEvent eventObject;

		private SenderRunner(ILoggingEvent eventObject) {
			this.eventObject = eventObject;
		}

		@Override
		public void run() {
			try {
				requestPost(makeSendText(eventObject).getBytes(StandardCharsets.UTF_8));

			} catch (Exception e) {
				addError("Slack Appender Exception", e);
				e.printStackTrace(System.err);
			}
		}
	}

	/**
	 * MultiSender
	 * 
	 * @author geunspage
	 *
	 */
	private class MultiSenderRunner implements Runnable {

		@Override
		public void run() {
			try {
				Thread.sleep(SEND_INTERVAL);
				if (LINKED_QUE.isEmpty()) {
					return;
				}

				int current_send_cnt = 0;
				List<String> sendList = new LinkedList<String>();

				while (LINKED_QUE.isEmpty() == false) {
					sendList.add(makeSendText(LINKED_QUE.poll()));
					current_send_cnt++;

					if (MAX_SEND_SIZE == current_send_cnt) {
						requestPost(toJsonArray(sendList).getBytes(StandardCharsets.UTF_8));
						sendList.clear();
						current_send_cnt = 0;
						Thread.sleep(500L);
					}
				}

				if (sendList.isEmpty() == false) {
					requestPost(toJsonArray(sendList).getBytes(StandardCharsets.UTF_8));
					sendList.clear();
				}

			} catch (Exception e) {
				addError("Slack Appender Exception", e);
				e.printStackTrace(System.err);
			} finally {
				IS_SEC_THREAD_RUN = false;
			}
		}

	}

	/*Setter Methods*/

	public void setWebhook_url(String webhook_url) {
		this.webhook_url = webhook_url;
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
