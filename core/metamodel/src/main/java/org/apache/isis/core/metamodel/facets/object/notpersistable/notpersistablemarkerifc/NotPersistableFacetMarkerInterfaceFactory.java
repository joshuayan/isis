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

package org.apache.isis.core.metamodel.facets.object.notpersistable.notpersistablemarkerifc;

import java.lang.reflect.Method;

import org.apache.isis.applib.annotation.NotPersistable;
import org.apache.isis.core.metamodel.facetapi.FacetHolder;
import org.apache.isis.core.metamodel.facetapi.FacetUtil;
import org.apache.isis.core.metamodel.facetapi.FeatureType;
import org.apache.isis.core.metamodel.facets.FacetFactoryAbstract;
import org.apache.isis.core.metamodel.facets.object.notpersistable.NotPersistableFacet;

public class NotPersistableFacetMarkerInterfaceFactory extends FacetFactoryAbstract {

    public NotPersistableFacetMarkerInterfaceFactory() {
        super(FeatureType.OBJECTS_ONLY);
    }

    public boolean recognizes(final Method method) {
        return false;
    }

    @Override
    public void process(final ProcessClassContext processClassContaxt) {
        final NotPersistable.By initiatedBy = NotPersistable.By.lookupForMarkerInterface(processClassContaxt.getCls());
        FacetUtil.addFacet(create(initiatedBy, processClassContaxt.getFacetHolder()));
    }

    private NotPersistableFacet create(final NotPersistable.By initiatedBy, final FacetHolder holder) {
        return initiatedBy != null ? new NotPersistableFacetMarkerInterface(initiatedBy, holder) : null;
    }

}
