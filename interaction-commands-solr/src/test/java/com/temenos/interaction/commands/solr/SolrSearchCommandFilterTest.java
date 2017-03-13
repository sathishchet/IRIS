package com.temenos.interaction.commands.solr;

/*
 * Unit tests for $filter parameter
 */

/* 
* #%L
 * * interaction-commands-solr
 * *
 * %%
 * Copyright (C) 2012 - 2013 Temenos Holdings N.V.
 * *
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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriInfo;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.temenos.interaction.authorization.command.AuthorizationAttributes;
import com.temenos.interaction.core.MultivaluedMapImpl;
import com.temenos.interaction.core.command.InteractionCommand;
import com.temenos.interaction.core.command.InteractionCommand.Result;
import com.temenos.interaction.core.command.InteractionContext;
import com.temenos.interaction.core.command.InteractionException;
import com.temenos.interaction.core.entity.Entity;
import com.temenos.interaction.core.entity.Metadata;
import com.temenos.interaction.core.hypermedia.ResourceState;
import com.temenos.interaction.core.resource.CollectionResource;


public class SolrSearchCommandFilterTest extends AbstractSolrTest {

	private @Mock ResourceState currentState;

	private SolrSearchCommand command;
	private InteractionContext ctx;
	private MultivaluedMap<String, String> queryParams;
	private MultivaluedMap<String, String> pathParams;


	@Before
	public void setup() {
		MockitoAnnotations.initMocks(this);

		command = new SolrSearchCommand();
		queryParams = new MultivaluedMapImpl<String>();
		pathParams = new MultivaluedMapImpl<String>();
		ctx = spy(new InteractionContext(
				mock(UriInfo.class),
				mock(HttpHeaders.class),
				pathParams,
				queryParams,
				mock(ResourceState.class),
				mock(Metadata.class)));

		when(currentState.getEntityName()).thenReturn(ENTITY1_TYPE);
		when(ctx.getCurrentState()).thenReturn(currentState);
	}

	/**
	 * Test for filtering on a single field
	 */
	@SuppressWarnings("unchecked")
	@Test
	public void testFilterSingleField() {
		pathParams.add("companyid", COMPANY_NAME);
		queryParams.add("$filter", "id eq 1111");
		try {
    		InteractionCommand.Result result = command.execute(ctx, entity1SolrServer);
    		assertEquals(Result.FAILURE, result);
		} catch (InteractionException e) {
		    fail("InteractionException : " + e.getHttpStatus().toString() + " - " + e.getMessage());
		}
		CollectionResource<Entity> cr = (CollectionResource<Entity>) ctx.getResource();
		assertEquals(1, cr.getEntities().size());
	}
	
	
	/**
	 * Test that filter done flag changes state.
	 */
	@Test
	public void testFilterDoneFlag() {
		pathParams.add("companyid", COMPANY_NAME);
		queryParams.add("$filter", "id eq 1111");
		// Set the flag to the not done state.
		ctx.setAttribute(AuthorizationAttributes.FILTER_DONE_ATTRIBUTE, Boolean.FALSE);
		try {
		command.execute(ctx, entity1SolrServer);
		} catch (InteractionException e) {
		    fail("InteractionException : " + e.getHttpStatus().toString() + " - " + e.getMessage());
		}
		
		// Check filtering has been done
		assertEquals(Boolean.TRUE, (Boolean) ctx.getAttribute(AuthorizationAttributes.FILTER_DONE_ATTRIBUTE));
	}

	/**
	 * Test for filtering on a single duplicate field
	 */
	@SuppressWarnings("unchecked")
	@Test
	public void testFilterSingleDuplicateField() {
		pathParams.add("companyid", COMPANY_NAME);
		queryParams.add("$filter", "id eq 1111");
		try {
    		InteractionCommand.Result result = command.execute(ctx, entity1SolrServer);
    		assertEquals(Result.SUCCESS, result);
		} catch (InteractionException e) {
		    fail("InteractionException : " + e.getHttpStatus().toString() + " - " + e.getMessage());
		}
		

		CollectionResource<Entity> cr = (CollectionResource<Entity>) ctx.getResource();
		assertEquals(1, cr.getEntities().size());
	}

	/**
	 * Test for filtering on multiple fields. Where item exists.
	 */
	@SuppressWarnings("unchecked")
	@Test
	public void testFilterMultipleFieldsHits() {
		pathParams.add("companyid", COMPANY_NAME);
		queryParams.add("$filter", "id eq 1111 and name eq 'Alan Jones'");
		try {
    		InteractionCommand.Result result = command.execute(ctx, entity1SolrServer);
    		assertEquals(Result.SUCCESS, result);
        } catch (InteractionException e) {
            fail("InteractionException : " + e.getHttpStatus().toString() + " - " + e.getMessage());
        }

		CollectionResource<Entity> cr = (CollectionResource<Entity>) ctx.getResource();
		assertEquals(1, cr.getEntities().size());
	}

	/**
	 * Test for filtering on multiple fields. Where item does not exist.
	 */
	@SuppressWarnings("unchecked")
	@Test
	public void testFilterMultipleFieldsMisses() {
		pathParams.add("companyid", COMPANY_NAME);
		queryParams.add("$filter", "id eq 1111 and name eq 'B Jillian'");
		try {
    		InteractionCommand.Result result = command.execute(ctx, entity1SolrServer);
    		assertEquals(Result.SUCCESS, result);
        } catch (InteractionException e) {
            fail("InteractionException : " + e.getHttpStatus().toString() + " - " + e.getMessage());
        }

		CollectionResource<Entity> cr = (CollectionResource<Entity>) ctx.getResource();
		assertEquals(0, cr.getEntities().size());
	}

	/**
	 * Test for filtering on a single field with near miss. i.e term is close
	 * but not exact. Since filtering is used in security, unlike the 'q='
	 * option only exact hits should be returned.
	 */
	@SuppressWarnings("unchecked")
	@Test
	public void testFilterSingleFieldMisses() {
		pathParams.add("companyid", COMPANY_NAME);
		queryParams.add("$filter", "id eq 1112");
		try {
    		InteractionCommand.Result result = command.execute(ctx, entity1SolrServer);
    		assertEquals(Result.SUCCESS, result);
        } catch (InteractionException e) {
            fail("InteractionException : " + e.getHttpStatus().toString() + " - " + e.getMessage());
        }

		CollectionResource<Entity> cr = (CollectionResource<Entity>) ctx.getResource();
		assertEquals(0, cr.getEntities().size());
	}

	/*
	 * Test throws on bad filter
	 */
	@Test
	public void testBadFilter() {
		pathParams.add("companyid", COMPANY_NAME);
		queryParams.add("$filter", "rubbish");

		boolean threw = false;
		try {
			command.execute(ctx, entity1SolrServer);
		} catch (Exception e) {
			threw = true;
		}
		assertTrue(threw);
	}
	
	/*
	 * Test does not throw on valid filter containing dangerous 'and', 'or' and 'eq' string.
	 */
	@Test
	public void testKeywordFilter() {
		pathParams.add("companyid", COMPANY_NAME);
		queryParams.add("$filter", "Landlord eq aequorin");

		boolean threw = false;
		try {
			command.execute(ctx, entity1SolrServer);
		} catch (Exception e) {
			threw = true;
		}
		assertFalse(threw);
	}
	
	/**
	 * Test for filtering on 'not equal' single field
	 */
	@SuppressWarnings("unchecked")
	@Test
	public void testFilterNotEqual() {
		pathParams.add("companyid", COMPANY_NAME);
		queryParams.add("$filter", "id ne 1111");
		try {
    		InteractionCommand.Result result = command.execute(ctx, entity1SolrServer);
    		assertEquals(Result.SUCCESS, result);
        } catch (InteractionException e) {
            fail("InteractionException : " + e.getHttpStatus().toString() + " - " + e.getMessage());
        }

		CollectionResource<Entity> cr = (CollectionResource<Entity>) ctx.getResource();
		
		// Should get 3 out of 4
		assertEquals(3, cr.getEntities().size());
	}
	
	/**
	 * Test for filtering on 'greater than' single field
	 */
	@SuppressWarnings("unchecked")
	@Test
	public void testFilterGreaterThan() {
		pathParams.add("companyid", COMPANY_NAME);
		queryParams.add("$filter", "id gt 3333");
		try {
    		InteractionCommand.Result result = command.execute(ctx, entity1SolrServer);
    		assertEquals(Result.SUCCESS, result);
        } catch (InteractionException e) {
            fail("InteractionException : " + e.getHttpStatus().toString() + " - " + e.getMessage());
        }

		CollectionResource<Entity> cr = (CollectionResource<Entity>) ctx.getResource();
		
		// Should get 4444
		assertEquals(1, cr.getEntities().size());
	}
	
	/**
	 * Test for filtering on 'less than' single field
	 */
	@SuppressWarnings("unchecked")
	@Test
	public void testFilterLessThan() {
		pathParams.add("companyid", COMPANY_NAME);
		queryParams.add("$filter", "id lt 2222");
		try {
    		InteractionCommand.Result result = command.execute(ctx, entity1SolrServer);
    		assertEquals(Result.SUCCESS, result);
        } catch (InteractionException e) {
            fail("InteractionException : " + e.getHttpStatus().toString() + " - " + e.getMessage());
        }

		CollectionResource<Entity> cr = (CollectionResource<Entity>) ctx.getResource();
		
		// Should get 1111
		assertEquals(1, cr.getEntities().size());
	}
	
	/**
	 * Test for filtering on a range. GE one value and LE another.
	 */
	@SuppressWarnings("unchecked")
	@Test
	public void testFilterRange() {
		pathParams.add("companyid", COMPANY_NAME);
		queryParams.add("$filter", "id gt 2222");
		queryParams.add("$filter", "id lt 3333");
		try {
    		InteractionCommand.Result result = command.execute(ctx, entity1SolrServer);
    		assertEquals(Result.SUCCESS, result);
        } catch (InteractionException e) {
            fail("InteractionException : " + e.getHttpStatus().toString() + " - " + e.getMessage());
        }

		CollectionResource<Entity> cr = (CollectionResource<Entity>) ctx.getResource();
		
		// Should get 2222 and 3333
		assertEquals(2, cr.getEntities().size());
	}
	
	/**
	 * Test for filtering on 'greater than or equal' single field
	 */
	@SuppressWarnings("unchecked")
	@Test
	public void testFilterGreaterThanEqual() {
		pathParams.add("companyid", COMPANY_NAME);
		queryParams.add("$filter", "id ge 3333");
		try {
    		InteractionCommand.Result result = command.execute(ctx, entity1SolrServer);
    		assertEquals(Result.SUCCESS, result);
        } catch (InteractionException e) {
            fail("InteractionException : " + e.getHttpStatus().toString() + " - " + e.getMessage());
        }

		CollectionResource<Entity> cr = (CollectionResource<Entity>) ctx.getResource();
		
		// Should get 3333 and 4444
		assertEquals(2, cr.getEntities().size());
	}
	
	/**
	 * Test for filtering on 'less than or equal' single field
	 */
	@SuppressWarnings("unchecked")
	@Test
	public void testFilterLessThanEqual() {
		pathParams.add("companyid", COMPANY_NAME);
		queryParams.add("$filter", "id le 2222");
		try {
    		InteractionCommand.Result result = command.execute(ctx, entity1SolrServer);
    		assertEquals(Result.SUCCESS, result);
        } catch (InteractionException e) {
            fail("InteractionException : " + e.getHttpStatus().toString() + " - " + e.getMessage());
        }

		CollectionResource<Entity> cr = (CollectionResource<Entity>) ctx.getResource();
		
		// Should get 1111 and 2222
		assertEquals(2, cr.getEntities().size());
	}



}
