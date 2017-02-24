package com.temenos.interaction.core.rim;

/*
 * #%L
 * interaction-core
 * %%
 * Copyright (C) 2012 - 2017 Temenos Holdings N.V.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * #L%
 */


import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.junit.Test;
import org.mockito.ArgumentCaptor;

import com.temenos.interaction.core.MultivaluedMapImpl;
import com.temenos.interaction.core.command.InteractionCommand;
import com.temenos.interaction.core.command.InteractionContext;
import com.temenos.interaction.core.entity.Metadata;
import com.temenos.interaction.core.hypermedia.BeanTransformer;
import com.temenos.interaction.core.hypermedia.Event;
import com.temenos.interaction.core.hypermedia.ResourceState;
import com.temenos.interaction.core.hypermedia.ResourceStateMachine;
import com.temenos.interaction.core.hypermedia.Transition;
import com.temenos.interaction.core.resource.EntityResource;
import com.temenos.interaction.core.rim.ResourceRequestConfig.Builder;

/**
 * Test Cases for SequentialResourceRequestHandler
 *
 * @author kprasanth
 *
 */
public class TestSequentialResourceRequestHandler {

    @Test
    public void testUriParametersAdded() {

        MultivaluedMap<String, String> newPathParameters = new MultivaluedMapImpl<String>();
        MultivaluedMap<String, String> queryParameters = new MultivaluedMapImpl<String>();
        Map<String, String> uriParameters = new HashMap<String, String>();
        queryParameters.put("id", Arrays.asList("123"));
        newPathParameters.put("companyid", Arrays.asList("US001"));
        uriParameters.put("entityname", "entityVal");

        HTTPHypermediaRIM rimHandler = mock(HTTPHypermediaRIM.class);
        Builder config = new ResourceRequestConfig.Builder();
        InteractionContext origContext = new InteractionContext(mock(UriInfo.class), null, newPathParameters,
                queryParameters, mock(ResourceState.class), mock(Metadata.class));
        ResourceStateMachine engine = new ResourceStateMachine(mock(ResourceState.class), new BeanTransformer());
        when(rimHandler.getHypermediaEngine()).thenReturn(engine);
        Transition t = new Transition.Builder().uriParameters(uriParameters).target(mockTarget("/test")).build();
        config.transition(t);
        SequentialResourceRequestHandler sequence = new SequentialResourceRequestHandler();
        Response mockResponse = mock(Response.class);
        when(mockResponse.getStatus()).thenReturn(200);
        GenericEntity<EntityResource<String>> mockEntity = new GenericEntity<EntityResource<String>>(
                new EntityResource("Foo", "EntityFoo"), EntityResource.class);
        when(mockResponse.getEntity()).thenReturn(mockEntity);
        when(
                rimHandler.handleRequest(any(HttpHeaders.class), any(InteractionContext.class), any(Event.class),
                        any(InteractionCommand.class), any(EntityResource.class), any(ResourceRequestConfig.class),
                        any(Boolean.class))).thenReturn(mockResponse);

        sequence.getResources(rimHandler, mock(HttpHeaders.class), origContext, mock(EntityResource.class),
                config.build());

        ArgumentCaptor<InteractionContext> ctxCapture = ArgumentCaptor.forClass(InteractionContext.class);
        verify(rimHandler).handleRequest(any(HttpHeaders.class), ctxCapture.capture(), any(Event.class),
                any(InteractionCommand.class), any(EntityResource.class), any(ResourceRequestConfig.class),
                any(Boolean.class));
        assertEquals("entityVal", ctxCapture.getValue().getPathParameters().getFirst("entityname"));
        assertEquals("US001", ctxCapture.getValue().getPathParameters().getFirst("companyid"));
        assertEquals("123", ctxCapture.getValue().getQueryParameters().getFirst("id"));
    }

    private ResourceState mockTarget(String path) {
        ResourceState target = mock(ResourceState.class);
        when(target.getName()).thenReturn("Bar");
        when(target.getPath()).thenReturn(path);
        when(target.getRel()).thenReturn("collection");
        return target;
    }
}
