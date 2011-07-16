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
package org.apache.isis.viewer.json.viewer.resources;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;

import org.apache.isis.core.commons.authentication.AuthenticationSession;
import org.apache.isis.core.metamodel.adapter.ObjectAdapter;
import org.apache.isis.core.metamodel.adapter.oid.stringable.OidStringifier;
import org.apache.isis.core.metamodel.consent.Consent;
import org.apache.isis.core.metamodel.facets.object.encodeable.EncodableFacet;
import org.apache.isis.core.metamodel.spec.ActionType;
import org.apache.isis.core.metamodel.spec.ObjectSpecification;
import org.apache.isis.core.metamodel.spec.SpecificationLoader;
import org.apache.isis.runtimes.dflt.runtime.system.context.IsisContext;
import org.apache.isis.runtimes.dflt.runtime.system.persistence.AdapterManager;
import org.apache.isis.runtimes.dflt.runtime.system.persistence.OidGenerator;
import org.apache.isis.runtimes.dflt.runtime.system.persistence.PersistenceSession;
import org.apache.isis.viewer.json.viewer.RepContext;
import org.apache.isis.viewer.json.viewer.ResourceContext;
import org.apache.isis.viewer.json.viewer.representations.Representation;
import org.apache.isis.viewer.json.viewer.representations.RepresentationBuilder;
import org.apache.isis.viewer.json.viewer.resources.objects.DomainObjectRepBuilder;
import org.apache.isis.viewer.json.viewer.util.OidUtils;
import org.apache.isis.viewer.json.viewer.util.UrlDecoderUtils;
import org.apache.isis.viewer.json.viewer.util.UrlParserUtils;
import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializationConfig;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;

public abstract class ResourceAbstract {

    private static final String HEADER_X_RESTFUL_OBJECTS_REASON = "X-RestfulObjects-Reason";


	protected final static ObjectMapper objectMapper = new ObjectMapper();
    static {
        objectMapper.configure(SerializationConfig.Feature.INDENT_OUTPUT, true);
    }

	public final static ActionType[] ACTION_TYPES = { ActionType.USER, ActionType.DEBUG, ActionType.EXPLORATION,
    // SET is excluded; we simply flatten contributed actions.
        };


    @Context
    HttpHeaders httpHeaders;

    @Context
    UriInfo uriInfo;

    @Context
    Request request;

    @Context
    HttpServletRequest httpServletRequest;

    @Context
    HttpServletResponse httpServletResponse;

    @Context
    SecurityContext securityContext;

    private ResourceContext resourceContext;


    protected void init() {
        this.resourceContext =
            new ResourceContext(httpHeaders, uriInfo, request, httpServletRequest, httpServletResponse, securityContext);
    }

    protected ResourceContext getResourceContext() {
        return resourceContext;
    }

    
    // //////////////////////////////////////////////////////////////
    // Rendering
    // //////////////////////////////////////////////////////////////

    protected String jsonRepresentionFrom(RepresentationBuilder builder) {
        Representation representation = builder.build();
        return asJson(representation);
    }

	protected String jsonRepresentationOf(
			final Collection<ObjectAdapter> collectionAdapters) {
		return asJson(Lists.newArrayList(
            Collections2.transform(collectionAdapters, toObjectSelfRepresentation())));
	}

	protected String jsonRepresentationOf(
			final ObjectAdapter objectAdapter) {
		return asJson(toObjectSelfRepresentation().apply(objectAdapter));
	}


    protected String asJson(final Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (JsonGenerationException e) {
            throw new RuntimeException(e);
        } catch (JsonMappingException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

	private Function<ObjectAdapter, Representation> toObjectSelfRepresentation() {
		final RepContext representationContext = getResourceContext().repContext();
        
        Function<ObjectAdapter, Representation> objectSelfRepresentation = 
            Functions.compose(
                DomainObjectRepBuilder.selfOf(), 
                DomainObjectRepBuilder.fromAdapter(representationContext));
		return objectSelfRepresentation;
	}


    // //////////////////////////////////////////////////////////////
    // Isis integration
    // //////////////////////////////////////////////////////////////

    protected ObjectSpecification getSpecification(final String specFullName) {
        return getSpecificationLoader().loadSpecification(specFullName);
    }

    protected ObjectAdapter getObjectAdapter(final String oidEncodedStr) {
        init();

        final ObjectAdapter objectAdapter = OidUtils.getObjectAdapter(oidEncodedStr, getOidStringifier());
        
        if (objectAdapter == null) {
            final String oidStr = UrlDecoderUtils.urlDecode(oidEncodedStr);
            throw new WebApplicationException(responseOfNotFound("could not determine adapter for OID: '" + oidStr + "'"));
        }
        return objectAdapter;
    }


    protected String getOidStr(final ObjectAdapter objectAdapter) {
        return OidUtils.getOidStr(objectAdapter, getOidStringifier());
    }



	protected static class ExpectedStringRepresentingValueException extends IllegalArgumentException {
		private static final long serialVersionUID = 1L;
	}
	protected static class ExpectedMapRepresentingReferenceException extends IllegalArgumentException {
		private static final long serialVersionUID = 1L;
	}
	protected static class UnknownOidException extends IllegalArgumentException {
		private static final long serialVersionUID = 1L;
		public UnknownOidException(String oid) {
		    super(UrlDecoderUtils.urlDecode(oid));
		}
	}
	
	/**
	 * 
	 * @param objectSpec - the {@link ObjectSpecification} to interpret the object as.
	 * @param node - expected to be either a String or a Map (ie from within a List, built by parsing a JSON structure). 
	 */
	protected ObjectAdapter objectAdapterFor(ObjectSpecification objectSpec, Object node) {
		// null
		if(node == null) {
			return null;
		}
		
		// value (encodable)
		if(objectSpec.isEncodeable()) {
			EncodableFacet encodableFacet = objectSpec.getFacet(EncodableFacet.class);
			if(!(node instanceof String)) {
				throw new ExpectedStringRepresentingValueException();
			} 
			String argStr = (String) node;
			return encodableFacet.fromEncodedString(argStr);
		}

		// reference
		try {
			@SuppressWarnings("unchecked")
			Map<String,Object> argLink = (Map<String,Object>) node;
			String oidFromHref = UrlParserUtils.oidFromHref(argLink);
			
			final ObjectAdapter objectAdapter = OidUtils.getObjectAdapter(oidFromHref, getOidStringifier());
			
			if (objectAdapter == null) {
			    throw new UnknownOidException(oidFromHref);
			}
			return objectAdapter;
		} catch (Exception e) {
			throw new ExpectedMapRepresentingReferenceException();
		}
	}

    // //////////////////////////////////////////////////////////////
    // Responses
    // //////////////////////////////////////////////////////////////

    protected static Response responseOfOk() {
        return Response.ok().build();
    }

    protected static Response responseOfGone(final String reason) {
        return Response.status(Status.GONE).header(HEADER_X_RESTFUL_OBJECTS_REASON, reason).build();
    }

    protected static Response responseOfBadRequest(final Consent consent) {
        return responseOfBadRequest(consent.getReason());
    }

    protected static Response responseOfNoContent(final String reason) {
        return Response.status(Status.NO_CONTENT).header(HEADER_X_RESTFUL_OBJECTS_REASON, reason).build();
    }

    protected static Response responseOfBadRequest(final String reason) {
        return Response.status(Status.BAD_REQUEST).header(HEADER_X_RESTFUL_OBJECTS_REASON, reason).build();
    }

    protected static Response responseOfNotFound(final IllegalArgumentException e) {
        return responseOfNotFound(e.getMessage());
    }

    protected static Response responseOfNotFound(final String reason) {
        return Response.status(Status.NOT_FOUND).header(HEADER_X_RESTFUL_OBJECTS_REASON, reason).build();
    }

    protected static Response responseOfPreconditionFailed(final String reason) {
        return Response.status(StatusTypes.PRECONDITION_FAILED).header(HEADER_X_RESTFUL_OBJECTS_REASON, reason).build();
    }

    protected static Response responseOfMethodNotAllowed(final String reason) {
        return Response.status(StatusTypes.METHOD_NOT_ALLOWED).header(HEADER_X_RESTFUL_OBJECTS_REASON, reason).build();
    }

    protected static Response responseOfInternalServerError(final Exception ex) {
        return responseOfInternalServerError(ex.getMessage());
    }

    protected static Response responseOfInternalServerError(final String reason) {
        return Response.status(Status.INTERNAL_SERVER_ERROR).header(HEADER_X_RESTFUL_OBJECTS_REASON, reason).build();
    }

    // //////////////////////////////////////////////////////////////
    // Dependencies (from singletons)
    // //////////////////////////////////////////////////////////////

    protected AuthenticationSession getSession() {
        return IsisContext.getAuthenticationSession();
    }

    private SpecificationLoader getSpecificationLoader() {
        return IsisContext.getSpecificationLoader();
    }

    protected AdapterManager getAdapterManager() {
        return getPersistenceSession().getAdapterManager();
    }

    protected PersistenceSession getPersistenceSession() {
        return IsisContext.getPersistenceSession();
    }

    private OidGenerator getOidGenerator() {
        return getPersistenceSession().getOidGenerator();
    }

    private OidStringifier getOidStringifier() {
        return getOidGenerator().getOidStringifier();
    }

    // //////////////////////////////////////////////////////////////
    // Dependencies (injected via @Context)
    // //////////////////////////////////////////////////////////////

    protected HttpServletRequest getServletRequest() {
        return getResourceContext().getHttpServletRequest();
    }

}