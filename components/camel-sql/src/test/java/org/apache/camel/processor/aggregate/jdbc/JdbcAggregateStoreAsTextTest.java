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
package org.apache.camel.processor.aggregate.jdbc;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.junit5.CamelSpringTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests to ensure that arbitrary headers can be stored as raw text within a dataSource Tests to ensure the body can be
 * stored as readable text within a dataSource
 */
public class JdbcAggregateStoreAsTextTest extends CamelSpringTestSupport {
    protected JdbcAggregationRepository repo;
    protected JdbcTemplate jdbcTemplate;
    protected DataSource dataSource;

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return newAppContext(
                "JdbcSpringDataSource.xml", "JdbcSpringAggregateStoreAsText.xml");
    }

    @Override
    public void postProcessTest() throws Exception {
        super.postProcessTest();

        repo = applicationContext.getBean("repo3", JdbcAggregationRepository.class);
        dataSource = context.getRegistry().lookupByNameAndType(getClass().getSimpleName() + "-dataSource3", DataSource.class);
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.afterPropertiesSet();

        jdbcTemplate.execute("DELETE FROM aggregationRepo3");
        jdbcTemplate.execute("DELETE FROM aggregationRepo3_completed");
    }

    @Test
    public void testStoreBodyAsTextAndCompanyNameHeaderAndAccountNameHeader() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:aggregated");
        mock.expectedBodiesReceived("ABCDE");

        repo.setStoreBodyAsText(true);
        repo.setHeadersToStoreAsText(Arrays.asList("companyName", "accountName"));

        Map<String, Object> headers = new HashMap<>();
        headers.put("id", 123);
        headers.put("companyName", "Acme");
        headers.put("accountName", "Alan");

        template.sendBodyAndHeaders("direct:start", "A", headers);
        assertEquals("A", getAggregationRepositoryBody(123));
        assertEquals("Acme", getAggregationRepositoryCompanyName(123));
        assertEquals("Alan", getAggregationRepositoryAccountName(123));

        template.sendBodyAndHeaders("direct:start", "B", headers);
        assertEquals("AB", getAggregationRepositoryBody(123));
        assertEquals("Acme", getAggregationRepositoryCompanyName(123));
        assertEquals("Alan", getAggregationRepositoryAccountName(123));

        template.sendBodyAndHeaders("direct:start", "C", headers);
        assertEquals("ABC", getAggregationRepositoryBody(123));
        assertEquals("Acme", getAggregationRepositoryCompanyName(123));
        assertEquals("Alan", getAggregationRepositoryAccountName(123));

        template.sendBodyAndHeaders("direct:start", "D", headers);
        assertEquals("ABCD", getAggregationRepositoryBody(123));
        assertEquals("Acme", getAggregationRepositoryCompanyName(123));
        assertEquals("Alan", getAggregationRepositoryAccountName(123));

        template.sendBodyAndHeaders("direct:start", "E", headers);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testStoreBodyAsTextAndNoHeaders() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:aggregated");
        mock.expectedBodiesReceived("ABCDE");

        repo.setStoreBodyAsText(true);
        repo.setHeadersToStoreAsText(null);

        Map<String, Object> headers = new HashMap<>();
        headers.put("id", 123);
        headers.put("companyName", "Acme");
        headers.put("accountName", "Alan");

        template.sendBodyAndHeaders("direct:start", "A", headers);
        assertEquals("A", getAggregationRepositoryBody(123));
        assertNull(getAggregationRepositoryCompanyName(123));
        assertNull(getAggregationRepositoryAccountName(123));

        template.sendBodyAndHeaders("direct:start", "B", headers);
        assertEquals("AB", getAggregationRepositoryBody(123));
        assertNull(getAggregationRepositoryCompanyName(123));
        assertNull(getAggregationRepositoryAccountName(123));

        template.sendBodyAndHeaders("direct:start", "C", headers);
        assertEquals("ABC", getAggregationRepositoryBody(123));
        assertNull(getAggregationRepositoryCompanyName(123));
        assertNull(getAggregationRepositoryAccountName(123));

        template.sendBodyAndHeaders("direct:start", "D", headers);
        assertEquals("ABCD", getAggregationRepositoryBody(123));
        assertNull(getAggregationRepositoryCompanyName(123));
        assertNull(getAggregationRepositoryAccountName(123));

        template.sendBodyAndHeaders("direct:start", "E", headers);

        MockEndpoint.assertIsSatisfied(context);
    }

    public String getAggregationRepositoryBody(int id) {
        return getAggregationRepositoryColumn(id, "body");
    }

    public String getAggregationRepositoryCompanyName(int id) {
        return getAggregationRepositoryColumn(id, "companyName");
    }

    public String getAggregationRepositoryAccountName(int id) {
        return getAggregationRepositoryColumn(id, "accountName");
    }

    public String getAggregationRepositoryColumn(int id, String columnName) {
        return jdbcTemplate.queryForObject("SELECT " + columnName + " from aggregationRepo3 where id = ?", String.class, id);
    }
}
