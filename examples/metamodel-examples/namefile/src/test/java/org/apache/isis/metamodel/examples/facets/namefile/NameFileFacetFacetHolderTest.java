/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */


package org.apache.isis.metamodel.examples.facets.namefile;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import org.jmock.Mockery;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.apache.isis.core.metamodel.facetapi.FacetHolder;
import org.apache.isis.metamodel.examples.facets.namefile.NameFileFacet;




@RunWith(JMock.class)
public class NameFileFacetFacetHolderTest {

    private final Mockery mockery = new JUnit4Mockery() {
        {
            setImposteriser(ClassImposteriser.INSTANCE);
        }
    };

    private NameFileFacet facet;
    private FacetHolder mockHolder;

    
    @Before
    public void setUp() throws Exception {
        mockHolder = mockery.mock(FacetHolder.class);
        facet = new NameFileFacet(mockHolder, "Foobar");
    }

    @After
    public void tearDown() throws Exception {
        mockHolder = null;
        facet = null;
    }


    @Test
    public void facetHolder() {
        assertThat(facet.getFacetHolder(), is(mockHolder));
    }

}