package no.nav.su.se.bakover.common

import com.ibm.mq.jms.MQConnectionFactory
import com.ibm.msg.client.wmq.WMQConstants
import javax.jms.JMSContext

/**
 * Wrapper class for JmsConfig
 */
data class JmsConfig(
    private val applicationConfig: ApplicationConfig,
) {

    val jmsContext: JMSContext by lazy {
        createJmsContext()
    }

    private fun createJmsContext(): JMSContext {
        return MQConnectionFactory().apply {
            applicationConfig.oppdrag.let {
                hostName = it.mqHostname
                port = it.mqPort
                channel = it.mqChannel
                queueManager = it.mqQueueManager
                transportType = WMQConstants.WMQ_CM_CLIENT
                setBooleanProperty(WMQConstants.USER_AUTHENTICATION_MQCSP, true)
            }
        }.createContext(applicationConfig.serviceUser.username, applicationConfig.serviceUser.password)
    }
}
