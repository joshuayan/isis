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
package org.apache.isis.applib.services.eventbus;

import org.apache.isis.applib.Identifier;
import org.apache.isis.applib.util.ObjectContracts;

public abstract class AbstractInteraction<S> extends java.util.EventObject {

    private static final long serialVersionUID = 1L;

    public AbstractInteraction(
            final S source,
            final Identifier identifier) {
        super(source);
        this.identifier = identifier;
    }

    //region > Mode

    public enum Mode {
        HIDE,
        DISABLE,
        VALIDATE,
        EXECUTE
    }

    private Mode mode;

    /**
     * Whether the framework is checking visibility, enablement, validity or actually executing (invoking action,
     * updating property or collection).
     */
    public Mode getMode() {
        return mode;
    }

    /**
     * Not API, set by the framework.
     */
    public void setMode(Mode mode) {
        this.mode = mode;
    }
    //endregion

    //region > source (downcast to S)
    @Override
    @SuppressWarnings("unchecked")
    public S getSource() {
        return (S)source;
    }
    //endregion

    //region > identifier
    private final Identifier identifier;
    public Identifier getIdentifier() {
        return identifier;
    }
    //endregion

    //region > toString
    @Override
    public String toString() {
        return ObjectContracts.toString(this, "source,mode,identifier");
    }
    //endregion
}