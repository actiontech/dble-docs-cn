package com.actiontech.addtionAlert;

import com.actiontech.dble.alarm.Alert;
import com.actiontech.dble.alarm.AlertGeneralConfig;
import com.actiontech.dble.cluster.bean.ClusterAlertBean;
import com.actiontech.dble.config.util.ConfigException;
import com.sun.mail.util.MailSSLSocketFactory;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * Created by szf on 2019/5/20.
 */
public class MailAlert implements Alert {

    private final static String MAIL_SENDER = "mail_sender";
    private final static String SENDER_PASSWORD = "sender_pass";
    private final static String MAIL_SERVER = "mail_server";
    private final static String MAIL_RECEIVE = "mail_receive";
    private final static String SERVER_ID = "server_id";
    private final static String COMPONNENT_TYPE = "DBLE";
    private final static String COMPONNENT_ID = "componnent_id";

    private static Properties properties;


    public MailAlert() {
        //init the mail data and read config file
        properties = AlertGeneralConfig.getInstance().getProperties();
    }


    @Override
    public void alertSelf(ClusterAlertBean clusterAlertBean) {
        alert(clusterAlertBean.setAlertComponentType(COMPONNENT_TYPE).setAlertComponentId(properties.getProperty(COMPONNENT_ID)));
    }

    @Override
    public void alert(ClusterAlertBean clusterAlertBean) {
        clusterAlertBean.setSourceComponentType(COMPONNENT_TYPE).
                setSourceComponentId(properties.getProperty(COMPONNENT_ID)).
                setServerId(properties.getProperty(SERVER_ID)).
                setTimestampUnix(System.currentTimeMillis() * 1000000);
        sendMail(false, clusterAlertBean);
    }

    @Override
    public boolean alertResolve(ClusterAlertBean clusterAlertBean) {
        clusterAlertBean.setSourceComponentType(COMPONNENT_TYPE).
                setSourceComponentId(properties.getProperty(COMPONNENT_ID)).
                setServerId(properties.getProperty(SERVER_ID)).
                setTimestampUnix(System.currentTimeMillis() * 1000000);
        return sendMail(true, clusterAlertBean);
    }

    @Override
    public boolean alertSelfResolve(ClusterAlertBean clusterAlertBean) {
        return alertResolve(clusterAlertBean.setAlertComponentType(COMPONNENT_TYPE).setAlertComponentId(properties.getProperty(COMPONNENT_ID)));
    }

    @Override
    public void alertConfigCheck() throws ConfigException {
        //check if the config is correct
        if (properties.getProperty(MAIL_SENDER) == null
                || properties.getProperty(SENDER_PASSWORD) == null
                || properties.getProperty(MAIL_SERVER) == null
                || properties.getProperty(MAIL_RECEIVE) == null) {
            throw new ConfigException("alert check error, for some config is missing");
        }
    }

    private boolean sendMail(boolean isResolve, ClusterAlertBean clusterAlertBean) {
        try {
            Properties props = new Properties();

            // 开启debug调试
            props.setProperty("mail.debug", "true");
            // 发送服务器需要身份验证
            props.setProperty("mail.smtp.auth", "true");
            // 设置邮件服务器主机名
            props.setProperty("mail.host", properties.getProperty(MAIL_SERVER));
            // 发送邮件协议名称
            props.setProperty("mail.transport.protocol", "smtp");

            MailSSLSocketFactory sf = new MailSSLSocketFactory();
            sf.setTrustAllHosts(true);
            props.put("mail.smtp.ssl.enable", "true");
            props.put("mail.smtp.ssl.socketFactory", sf);

            Session session = Session.getInstance(props);

            Message msg = new MimeMessage(session);
            msg.setSubject("DBLE告警 " + (isResolve ? "RESOLVE\n" : "ALERT\n"));
            StringBuilder builder = new StringBuilder();
            builder.append(groupMailMsg(clusterAlertBean, isResolve));
            msg.setText(builder.toString());
            msg.setFrom(new InternetAddress(properties.getProperty(MAIL_SENDER)));

            Transport transport = session.getTransport();
            transport.connect(properties.getProperty(MAIL_SERVER), properties.getProperty(MAIL_SENDER), properties.getProperty(SENDER_PASSWORD));

            transport.sendMessage(msg, new Address[]{new InternetAddress(properties.getProperty(MAIL_RECEIVE))});
            transport.close();
            //send EMAIL SUCCESS return TRUE
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        //send fail reutrn false
        return false;
    }

    private String groupMailMsg(ClusterAlertBean clusterAlertBean, boolean isResolve) {
        StringBuffer sb = new StringBuffer("Alert mail:\n");
        sb.append("         Alert type:" + clusterAlertBean.getCode() + " " + (isResolve ? "RESOLVE\n" : "ALERT\n"));
        sb.append("         Alert message:" + clusterAlertBean.getDesc() + "\n");
        sb.append("         Alert componnent:" + clusterAlertBean.getAlertComponentType() + "\n");
        sb.append("         Alert componnentID:" + clusterAlertBean.getAlertComponentId() + "\n");
        sb.append("         Alert source:" + clusterAlertBean.getAlertComponentId() + "\n");
        sb.append("         Alert server:" + clusterAlertBean.getServerId() + "\n");
        sb.append("         Alert time:" + TimeStamp2Date(clusterAlertBean.getTimestampUnix()) + "\n");
        String detail = "|";
        if (clusterAlertBean.getLabels() != null) {
            for (Map.Entry<String, String> entry : clusterAlertBean.getLabels().entrySet()) {
                detail += entry.getKey() + ":" + entry.getValue();
            }
        }
        sb.append("         Other detail:" + detail + "|\n");
        return sb.toString();
    }

    public String TimeStamp2Date(long timestampString) {
        String formats = "yyyy-MM-dd HH:mm:ss";
        Long timestamp = timestampString / 1000000;
        String date = new SimpleDateFormat(formats, Locale.CHINA).format(new Date(timestamp));
        return date;
    }
}
