/*
 * #%L
 * Wildfly Camel :: Example :: Camel JMS
 * %%
 * Copyright (C) 2013 - 2014 RedHat
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package org.wildfly.camel.examples.jms;

import javax.ejb.Startup;
import javax.enterprise.context.ApplicationScoped;
import javax.jms.ConnectionFactory;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.JmsComponent;
import org.wildfly.extension.camel.CamelAware;
import javax.naming.Context;
import javax.naming.InitialContext;
import java.util.Properties;

@Startup
@CamelAware
@ApplicationScoped
public class JmsRouteBuilder extends RouteBuilder {

    //CHANGED: NO LONGER NEEDED (looked up via new InitialContext/remote)
    // @Resource(mappedName = "java:/ConnectionFactory")
    // private ConnectionFactory connectionFactory;

    @Override
    public void configure() throws Exception {


        //TAKEN FROM https://developer.jboss.org/thread/270107
        // final Properties env = new Properties();  
        // env.put(Context.INITIAL_CONTEXT_FACTORY, "org.jboss.naming.remote.client.InitialContextFactory");  
        // env.put(Context.PROVIDER_URL, "remote://127.0.0.1:4447");  
        // context = new InitialContext(env);  
        // ConnectionFactory cf = (ConnectionFactory) context.lookup("jms/HornetQRemoteConnectionFactory");  
        // Queue queue = (Queue) context.lookup("jms/queue/LegacyMyQueue");  

        Properties props = new Properties();
        props.put(Context.INITIAL_CONTEXT_FACTORY, "org.jboss.naming.remote.client.InitialContextFactory");
        props.put(Context.PROVIDER_URL, "remote://127.0.0.1:4447");    
        InitialContext context = new InitialContext(props);     
        ConnectionFactory connectionFactory = (ConnectionFactory) context.lookup("jms/HornetQRemoteConnectionFactory");

        /**
         * Configure the JMSComponent to use the connection factory
         * injected into this class
         */
        JmsComponent component = new JmsComponent();
        component.setConnectionFactory(connectionFactory);

        getContext().addComponent("jms", component);

        /**
         * This route reads files placed within JBOSS_HOME/standalone/data/orders
         * and places them onto JMS queue 'ordersQueue' within the WildFly
         * internal HornetQ broker.
         */
        from("file://{{jboss.server.data.dir}}/orders")
        .convertBodyTo(String.class)
        // Remove headers to ensure we end up with unique file names being generated in the next route
        .removeHeaders("*")
        .to("jms:queue:MyQueue");

        /**
         * This route consumes messages from the 'ordersQueue'. Then, based on the
         * message payload XML content it uses a content based router to output
         * orders into appropriate country directories
         */
        from("jms:queue:MyQueue")
            .choice()
                .when(xpath("/order/customer/country = 'UK'"))
                    .log("Sending order ${file:name} to the UK")
                    .to("file:{{jboss.server.data.dir}}/orders/processed/UK")
                .when(xpath("/order/customer/country = 'US'"))
                    .log("Sending order ${file:name} to the US")
                    .to("file:{{jboss.server.data.dir}}/orders/processed/US")
                .otherwise()
                    .log("Sending order ${file:name} to another country")
                    .to("file://{{jboss.server.data.dir}}/orders/processed/others");
    }
}
