/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.file;

import java.nio.file.Files;
import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.camel.spi.Registry;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test for the idempotentRepositoryRef option.
 */
public class FileConsumerIdempotentRefTest extends ContextTestSupport {

    private static volatile boolean invoked;

    @Override
    protected Registry createRegistry() throws Exception {
        Registry jndi = super.createRegistry();
        jndi.bind("myRepo", new MyIdempotentRepository());
        return jndi;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from(fileUri("?idempotent=true&idempotentRepository=#myRepo&move=done/${file:name}&initialDelay=0&delay=10"))
                        .convertBodyTo(String.class)
                        .to("mock:result");
            }
        };
    }

    @Test
    public void testIdempotentRef() throws Exception {
        template.sendBodyAndHeader(fileUri(), "Hello World", Exchange.FILE_NAME, "report.txt");

        // consume the file the first time
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");
        mock.expectedMessageCount(1);

        assertMockEndpointsSatisfied();

        oneExchangeDone.matchesWaitTime();

        // reset mock and set new expectations
        mock.reset();
        mock.expectedMessageCount(0);

        // move file back
        Files.move(testFile("done/report.txt"), testFile("report.txt"));

        // should NOT consume the file again, let a bit time go
        Awaitility.await().pollDelay(100, TimeUnit.MILLISECONDS).untilAsserted(() -> {
            assertMockEndpointsSatisfied();
        });

        assertTrue(invoked, "MyIdempotentRepository should have been invoked");
    }

    public class MyIdempotentRepository implements IdempotentRepository {

        @Override
        public boolean add(String messageId) {
            // will return true 1st time, and false 2nd time
            boolean result = invoked;
            invoked = true;
            assertEquals(testFile("report.txt").toAbsolutePath().toString(), messageId);
            return !result;
        }

        @Override
        public boolean contains(String key) {
            return invoked;
        }

        @Override
        public boolean remove(String key) {
            return true;
        }

        @Override
        public boolean confirm(String key) {
            return true;
        }

        @Override
        public void clear() {
            return;
        }

        @Override
        public void start() {
        }

        @Override
        public void stop() {
        }
    }

}
