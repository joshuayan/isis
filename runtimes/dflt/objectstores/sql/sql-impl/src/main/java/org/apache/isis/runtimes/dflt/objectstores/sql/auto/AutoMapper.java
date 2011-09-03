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

package org.apache.isis.runtimes.dflt.objectstores.sql.auto;

import java.util.Vector;

import org.apache.log4j.Logger;

import org.apache.isis.core.commons.debug.DebugBuilder;
import org.apache.isis.core.commons.debug.DebuggableWithTitle;
import org.apache.isis.core.metamodel.adapter.ObjectAdapter;
import org.apache.isis.core.metamodel.adapter.ResolveState;
import org.apache.isis.core.metamodel.adapter.oid.Oid;
import org.apache.isis.core.metamodel.adapter.version.SerialNumberVersion;
import org.apache.isis.core.metamodel.spec.ObjectSpecification;
import org.apache.isis.core.metamodel.spec.feature.ObjectAssociation;
import org.apache.isis.runtimes.dflt.objectstores.sql.CollectionMapper;
import org.apache.isis.runtimes.dflt.objectstores.sql.DatabaseConnector;
import org.apache.isis.runtimes.dflt.objectstores.sql.FieldMappingLookup;
import org.apache.isis.runtimes.dflt.objectstores.sql.IdMapping;
import org.apache.isis.runtimes.dflt.objectstores.sql.ObjectMapping;
import org.apache.isis.runtimes.dflt.objectstores.sql.ObjectMappingLookup;
import org.apache.isis.runtimes.dflt.objectstores.sql.Results;
import org.apache.isis.runtimes.dflt.objectstores.sql.SqlObjectStoreException;
import org.apache.isis.runtimes.dflt.objectstores.sql.TitleMapping;
import org.apache.isis.runtimes.dflt.objectstores.sql.VersionMapping;
import org.apache.isis.runtimes.dflt.objectstores.sql.mapping.FieldMapping;
import org.apache.isis.runtimes.dflt.runtime.persistence.ConcurrencyException;
import org.apache.isis.runtimes.dflt.runtime.persistence.ObjectNotFoundException;
import org.apache.isis.runtimes.dflt.runtime.persistence.PersistorUtil;
import org.apache.isis.runtimes.dflt.runtime.persistence.query.PersistenceQueryFindByPattern;

public class AutoMapper extends AbstractAutoMapper implements ObjectMapping, DebuggableWithTitle {
    private static final Logger LOG = Logger.getLogger(AutoMapper.class);
    private static final int MAX_INSTANCES = 100;
    private final IdMapping idMapping;
    private final VersionMapping versionMapping;
    private final TitleMapping titleMapping;

    public AutoMapper(final String className, final String parameterBase, final FieldMappingLookup lookup,
        final ObjectMappingLookup objectMapperLookup) {
        super(className, parameterBase, lookup, objectMapperLookup);
        idMapping = lookup.createIdMapping();
        versionMapping = lookup.createVersionMapping();
        titleMapping = lookup.createTitleMapping();
    }

    @Override
    public void createTables(final DatabaseConnector connection) {
        if (!connection.hasTable(table)) {
            final StringBuffer sql = new StringBuffer();
            sql.append("create table ");
            sql.append(table);
            sql.append(" (");
            idMapping.appendCreateColumnDefinitions(sql);
            sql.append(", ");
            for (final FieldMapping mapping : fieldMappings) {
                mapping.appendColumnDefinitions(sql);
                sql.append(",");
            }
            titleMapping.appendColumnDefinitions(sql);
            sql.append(", ");
            sql.append(versionMapping.appendColumnDefinitions());
            sql.append(")");
            connection.update(sql.toString());
        }
        for (int i = 0; collectionMappers != null && i < collectionMappers.length; i++) {
            if (collectionMappers[i].needsTables(connection)) {
                collectionMappers[i].createTables(connection);
            }
        }
    }

    @Override
    public void createObject(final DatabaseConnector connector, final ObjectAdapter object) {
        final int versionSequence = 1;
        final SerialNumberVersion version = createVersion(versionSequence);

        final StringBuffer sql = new StringBuffer();
        sql.append("insert into " + table + " (");
        idMapping.appendColumnNames(sql);
        sql.append(", ");
        final String columnList = columnList();
        if (columnList.length() > 0) {
            sql.append(columnList);
            sql.append(", ");
        }
        titleMapping.appendColumnNames(sql);
        sql.append(", ");
        sql.append(versionMapping.insertColumns());
        sql.append(") values (");
        idMapping.appendInsertValues(connector, sql, object);
        sql.append(", ");
        sql.append(values(connector, object));
        titleMapping.appendInsertValues(connector, sql, object);
        sql.append(", ");
        sql.append(versionMapping.insertValues(connector, version));
        sql.append(") ");

        connector.insert(sql.toString());
        object.setOptimisticLock(version);

        for (final CollectionMapper collectionMapper : collectionMappers) {
            collectionMapper.saveInternalCollection(connector, object);
        }
    }

    @Override
    public void destroyObject(final DatabaseConnector connector, final ObjectAdapter object) {
        final StringBuffer sql = new StringBuffer();
        sql.append("delete from " + table + " WHERE ");
        idMapping.appendWhereClause(connector, sql, object.getOid());
        sql.append(" AND ");
        sql.append(versionMapping.whereClause(connector, (SerialNumberVersion) object.getVersion()));
        final int updateCount = connector.update(sql.toString());
        if (updateCount == 0) {
            LOG.info("concurrency conflict object " + this + "; no deletion performed");
            throw new ConcurrencyException("", object.getOid());
        }
    }

    @Override
    public ObjectAdapter[] getInstances(final DatabaseConnector connector, final ObjectSpecification spec) {
        final StringBuffer sql = createSelectStatement();
        return loadInstances(connector, spec, completeSelectStatement(sql));
    }

    @Override
    public ObjectAdapter[] getInstances(final DatabaseConnector connector, final ObjectSpecification spec,
        final PersistenceQueryFindByPattern query) {
        final StringBuffer sql = createSelectStatement();
        sql.append(" WHERE ");

        final int initialLength = sql.length();
        final ObjectAdapter pattern = query.getPattern();
        for (final ObjectAssociation assoc : specification.getAssociations()) {
            final ObjectAdapter field = assoc.get(pattern);
            if (field != null) {
                final FieldMapping fieldMapping = fieldMappingFor(assoc);
                if (fieldMapping != null) {
                    if (sql.length() > initialLength) {
                        sql.append(" AND ");
                    }
                    fieldMapping.appendWhereClause(connector, sql, pattern);
                }
            }
        }
        return loadInstances(connector, spec, completeSelectStatement(sql));
    }

    @Override
    public ObjectAdapter[] getInstances(final DatabaseConnector connector, final ObjectSpecification spec,
        final String title) {
        final StringBuffer sql = createSelectStatement();
        sql.append(" WHERE ");
        titleMapping.appendWhereClause(sql, title);
        return loadInstances(connector, spec, completeSelectStatement(sql));
    }

    @Override
    public ObjectAdapter getObject(final DatabaseConnector connector, final Oid oid, final ObjectSpecification hint) {
        final StringBuffer sql = createSelectStatement();
        sql.append(" WHERE ");
        idMapping.appendWhereClause(connector, sql, oid);
        final Results rs = connector.select(completeSelectStatement(sql));
        if (rs.next()) {
            return loadObject(connector, hint, rs);
        } else {
            throw new ObjectNotFoundException("No object with with " + oid + " in table " + table);
        }
    }

    @Override
    public boolean hasInstances(final DatabaseConnector connector, final ObjectSpecification cls) {
        final String statement = "select count(*) from " + table;
        final int instances = connector.count(statement);
        return instances > 0;
    }

    private StringBuffer createSelectStatement() {
        final StringBuffer sql = new StringBuffer();
        sql.append("select ");
        idMapping.appendColumnNames(sql);
        sql.append(", ");
        final String columnList = columnList();
        if (columnList.length() > 0) {
            sql.append(columnList);
            sql.append(", ");
        }
        sql.append(versionMapping.insertColumns());
        sql.append(" from " + table);
        return sql;
    } /*
       * if (whereClause != null) { sql.append(" WHERE "); sql.append(whereClause); } else if (whereClause != null) {
       * sql.append(" WHERE "); idMapping.appendWhereClause(sql, oid); }
       */

    private String completeSelectStatement(final StringBuffer sql) {
        sql.append(" order by ");
        idMapping.appendColumnNames(sql);
        return sql.toString();
    }

    protected void loadFields(final ObjectAdapter object, final Results rs) {
        PersistorUtil.start(object, ResolveState.RESOLVING);
        for (final FieldMapping mapping : fieldMappings) {
            mapping.initializeField(object, rs);
        }
        /*
         * for (int i = 0; i < oneToManyProperties.length; i++) { /* Need to set up collection to be a ghost before we
         * access as below
         */
        // CollectionAdapter collection = (CollectionAdapter)
        /*
         * oneToManyProperties[i].get(object); }
         */
        object.setOptimisticLock(versionMapping.getLock(rs));
        PersistorUtil.end(object);
    }

    // KAM
    private void loadCollections(final DatabaseConnector connector, final ObjectAdapter instance) {

        for (final CollectionMapper mapper : collectionMappers) {
            mapper.loadInternalCollection(connector, instance);
        }
    }

    private ObjectAdapter[] loadInstances(final DatabaseConnector connector, final ObjectSpecification cls,
        final String selectStatment) {
        LOG.debug("loading instances from SQL " + table);
        final Vector<ObjectAdapter> instances = new Vector<ObjectAdapter>();

        final Results rs = connector.select(selectStatment);
        for (int count = 0; rs.next() && count < MAX_INSTANCES; count++) {
            final ObjectAdapter instance = loadObject(connector, cls, rs);
            LOG.debug("  instance  " + instance);
            instances.addElement(instance);
        }
        rs.close();

        final ObjectAdapter[] array = new ObjectAdapter[instances.size()];
        instances.copyInto(array);
        return array;
    }

    private ObjectAdapter loadObject(final DatabaseConnector connector, final ObjectSpecification cls, final Results rs) {
        final Oid oid = idMapping.recreateOid(rs, specification);
        final ObjectAdapter instance = getAdapter(cls, oid);

        if (instance.getResolveState().isValidToChangeTo(ResolveState.RESOLVING)) {
            loadFields(instance, rs);
            loadCollections(connector, instance); // KAM
        }
        return instance;
    }

    @Override
    public void resolve(final DatabaseConnector connector, final ObjectAdapter object) {
        LOG.debug("loading data from SQL " + table + " for " + object);
        final StringBuffer sql = new StringBuffer();
        sql.append("select ");
        sql.append(columnList());
        sql.append(",");
        sql.append(versionMapping.appendSelectColumns());
        sql.append(" from " + table + " WHERE ");
        idMapping.appendWhereClause(connector, sql, object.getOid());

        final Results rs = connector.select(sql.toString());
        if (rs.next()) {
            loadFields(object, rs);
            rs.close();

            for (final CollectionMapper collectionMapper : collectionMappers) {
                collectionMapper.loadInternalCollection(connector, object);
            }
        } else {
            rs.close();
            throw new SqlObjectStoreException("Unable to load data from " + table + " with id "
                + idMapping.primaryKey(object.getOid()));
        }
    }

    @Override
    public void resolveCollection(final DatabaseConnector connector, final ObjectAdapter object,
        final ObjectAssociation field) {
        if (collectionMappers.length > 0) {
            final DatabaseConnector secondConnector = connector.getConnectionPool().acquire();
            for (final CollectionMapper collectionMapper : collectionMappers) {
                collectionMapper.loadInternalCollection(secondConnector, object);
            }
            connector.getConnectionPool().release(secondConnector);
        }
    }

    @Override
    public void startup(final DatabaseConnector connector, final ObjectMappingLookup objectMapperLookup) {
        if (needsTables(connector)) {
            createTables(connector);
        }
    }

    @Override
    public void save(final DatabaseConnector connector, final ObjectAdapter object) {
        final SerialNumberVersion version = (SerialNumberVersion) object.getVersion();
        final long nextSequence = version.getSequence() + 1;

        final StringBuffer sql = new StringBuffer();
        sql.append("UPDATE " + table + " SET ");
        for (final FieldMapping mapping : fieldMappings) {
            mapping.appendUpdateValues(connector, sql, object);
            sql.append(", ");
        }
        sql.append(versionMapping.updateAssigment(connector, nextSequence));
        sql.append(", ");
        titleMapping.appendUpdateAssignment(connector, sql, object);
        sql.append(" WHERE ");
        idMapping.appendWhereClause(connector, sql, object.getOid());
        sql.append(" AND ");
        sql.append(versionMapping.whereClause(connector, (SerialNumberVersion) object.getVersion()));

        final int updateCount = connector.update(sql.toString());
        if (updateCount == 0) {
            LOG.info("concurrency conflict object " + this + "; no update performed");
            throw new ConcurrencyException("", object.getOid());
        } else {
            object.setOptimisticLock(createVersion(nextSequence));
        }

        // TODO update collections - change only when needed rather than
        // reinserting from scratch
        for (final CollectionMapper collectionMapper : collectionMappers) {
            collectionMapper.saveInternalCollection(connector, object);
        }
    }

    @Override
    public String toString() {
        return "AutoMapper [table=" + table + ",id=" + idMapping + ",noColumns=" + fieldMappings.size()
            + ",specification=" + specification.getFullIdentifier() + "]";
    }

    @Override
    public void debugData(final DebugBuilder debug) {
        debug.appendln("ID mapping", idMapping);
        debug.appendln("ID mapping", versionMapping);
        debug.appendln("ID mapping", titleMapping);
        for (final FieldMapping mapping : fieldMappings) {
            mapping.debugData(debug);
        }
        for (final CollectionMapper collectionMapper : collectionMappers) {
            collectionMapper.debugData(debug);
        }

    }

    @Override
    public String debugTitle() {
        return toString();
    }

}