# slack-appender
##
* Logback appdender to use Slack Message.

##### Maven
    <dependency>
            <groupId>kr.geun.logback</groupId>
            <artifactId>slack-appender</artifactId>
            <version>0.0.1</version>
    </dependency>

#### Require Parameters
| Parameter        | Type           | Desc  |
| ------------- |:-------------:| :-----|
| webhook_url | String | Published Slack Webhook Url |
| channel | String |Channel to receive Slack Message |
| noti_level | String | Log level to receive notifications |
| layout | String | Slack Message Layout |

#### How to Use
* logback.xml

	<?xml version="1.0" encoding="UTF-8" ?>
	<configuration scan="true" scanPeriod="30 seconds">
	
		<appender name="SLACK" class="kr.geun.logback.appender.SlackAppender">
			<webhook_url>{Your Slack Webhook Url : https://hooks.slack.com/services/XXXXXXXXXXXXXXX}</webhook_url>
			<channel>#{slack channel : #err_noti}</channel>
			<noti_level>{log notification level : WARN}</noti_level>
			<layout>
	            <pattern>{layout pattern : %d{yyyy-MM-dd HH:mm:ss.SSS} %-5level %logger{36} - %msg%n }</pattern>
	        </layout>
		</appender>
				
		<root>
			<level value="DEBUG" />
			<appender-ref ref="SLACK" />
		</root>
	</configuration>