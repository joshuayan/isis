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

package org.apache.isis.core.metamodel.facets.propparam.labelat;

import org.apache.isis.applib.annotation.LabelAt;
import org.apache.isis.core.metamodel.facetapi.FacetHolder;

/**
 * If multi-line then position the label at the top.
 *
 * <p>
 *     This can still be overridden using the {@link org.apache.isis.applib.annotation.LabelAt} annotation / layout.json.
 * </p>
 */
public class LabelAtFacetInferredFromMultiLineFacet extends LabelAtFacetAbstract {

    public LabelAtFacetInferredFromMultiLineFacet(final FacetHolder holder) {
        super(LabelAt.Position.TOP, holder);
    }

}