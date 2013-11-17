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

package org.apache.isis.viewer.wicket.ui.components.scalars.string;

import org.apache.wicket.Component;

import org.apache.isis.core.metamodel.facets.multiline.MultiLineFacet;
import org.apache.isis.viewer.wicket.model.models.ScalarModel;
import org.apache.isis.viewer.wicket.ui.components.scalars.ComponentFactoryScalarAbstract;

public class StringPanelFactory extends ComponentFactoryScalarAbstract {

    private static final long serialVersionUID = 1L;

    public StringPanelFactory() {
        super(
            StringPanel.class, 
            String.class);
    }

    @Override
    public Component createComponent(final String id, final ScalarModel scalarModel) {
        final MultiLineFacet multiLineFacet = scalarModel.getFacet(MultiLineFacet.class);
        if (multiLineFacet != null && !multiLineFacet.isNoop()) {
            return new MultiLineStringPanel(id, scalarModel);
        } else {
            return new StringPanel(id, scalarModel);
        }
    }
}
