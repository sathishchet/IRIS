package com.temenos.interaction.core.hypermedia;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.core.MultivaluedMap;

import org.junit.Before;
import org.junit.Test;

import com.temenos.interaction.core.MultivaluedMapImpl;
import com.temenos.interaction.core.hypermedia.ResourceState;
import com.temenos.interaction.core.hypermedia.ResourceStateMachine;
import com.temenos.interaction.core.hypermedia.validation.HypermediaValidator;
import com.temenos.interaction.core.resource.CollectionResource;
import com.temenos.interaction.core.resource.EntityResource;
import com.temenos.interaction.core.web.RequestContext;

public class TestResourceStateMachine {

	@Before
	public void setup() {
		// initialise the thread local request context with requestUri and baseUri
        RequestContext ctx = new RequestContext("/baseuri", "/requesturi", null);
        RequestContext.setRequestContext(ctx);
	}

	/*
	 * Evaluate custom link relation, via the Link header.  See (see rfc5988)
	 * We return a Link if the header is set and the @{link Transition} can be found.
	 */
	@SuppressWarnings("unchecked")
	@Test
	public void testGetLinkForCustomLinkRelation() {
		ResourceState existsState = new ResourceState("toaster", "exists", new HashSet<Action>(), "/machines/toaster");
		ResourceState cookingState = new ResourceState("toaster", "cooking", new HashSet<Action>(), "/machines/toaster/cooking");

		// view the resource if the toaster is cooking (could be time remaining)
		existsState.addTransition("GET", cookingState);
		// stop the toast cooking
		cookingState.addTransition("DELETE", existsState);
		// the entity for linkage mapping
		EntityResource<Object> testResponseEntity = new EntityResource<Object>(null);
		// initialise application state
		ResourceStateMachine stateMachine = new ResourceStateMachine(existsState);

		// mock the Link header
		LinkHeader linkHeader = LinkHeader.valueOf("</path>; rel=\"toaster.cooking>toaster.exists\"");

		Link targetLink = stateMachine.getLinkFromRelations(mock(MultivaluedMap.class), testResponseEntity, linkHeader);

		assertNotNull(targetLink);
		assertEquals("/baseuri/machines/toaster", targetLink.getHref());
		assertEquals("toaster.cooking>toaster.exists", targetLink.getId());
	}


	/*
	 * We return a Link if a @{link Transition} for supplied method can be found.
	 */
	@SuppressWarnings("unchecked")
	@Test
	public void testGetLinkForTargetState() {
		ResourceState existsState = new ResourceState("toaster", "exists", new HashSet<Action>(), "/machines/toaster");
		ResourceState cookingState = new ResourceState("toaster", "cooking", new HashSet<Action>(), "/machines/toaster/cooking");

		// view the resource if the toaster is cooking (could be time remaining)
		existsState.addTransition("GET", cookingState);
		// stop the toast cooking
		cookingState.addTransition("DELETE", existsState);
		// the entity for linkage mapping
		EntityResource<Object> testResponseEntity = new EntityResource<Object>(null);
		// initialise application state
		ResourceStateMachine stateMachine = new ResourceStateMachine(existsState);

		Link targetLink = stateMachine.getLinkFromMethod(mock(MultivaluedMap.class), testResponseEntity, cookingState, "DELETE");

		assertNotNull(targetLink);
		assertEquals("/baseuri/machines/toaster", targetLink.getHref());
		assertEquals("toaster.cooking>toaster.exists", targetLink.getId());
	}

	/*
	 * We return a Link if a @{link Transition} for supplied method can be found.
	 */
	@SuppressWarnings("unchecked")
	@Test
	public void testGetLinkForSelfState() {
		CollectionResourceState collectionState = new CollectionResourceState("machines", "MachineView", new HashSet<Action>(), "/machines");

		// create machines
		collectionState.addTransition("POST", collectionState);
		// the entity for linkage mapping
		EntityResource<Object> testResponseEntity = new EntityResource<Object>(null);
		// initialise application state
		ResourceStateMachine stateMachine = new ResourceStateMachine(collectionState);

		Link targetLink = stateMachine.getLinkFromMethod(mock(MultivaluedMap.class), testResponseEntity, collectionState, "POST");

		assertNotNull(targetLink);
		// a target link the same as our current state equates to 205 Reset Content
		assertEquals("/baseuri/machines", targetLink.getHref());
		// we use createSelfLink under the covers so link id looks like transition to self
		assertEquals("machines.MachineView>machines.MachineView", targetLink.getId());
	}

	/*
	 * We return a Link if a @{link Transition} for supplied method can be found.
	 * When the target state is a pseudo final state, no link will be returned.
	 */
	@SuppressWarnings("unchecked")
	@Test
	public void testGetLinkForFinalPseudoState() {
		ResourceState existsState = new ResourceState("toaster", "exists", new HashSet<Action>(), "/machines/toaster/{id}");
		ResourceState deletedState = new ResourceState(existsState, "deleted", new HashSet<Action>());

		// delete the toaster
		existsState.addTransition("DELETE", deletedState);
		// the entity for linkage mapping
		EntityResource<Object> testResponseEntity = new EntityResource<Object>(null);
		// initialise application state
		ResourceStateMachine stateMachine = new ResourceStateMachine(existsState);

		Link targetLink = stateMachine.getLinkFromMethod(mock(MultivaluedMap.class), testResponseEntity, existsState, "DELETE");

		// no target link equates to 204 No Content
		assertNull(targetLink);
	}

	@Test
	public void testStates() {
		String ENTITY_NAME = "T24CONTRACT";
		ResourceState begin = new ResourceState(ENTITY_NAME, "begin", new HashSet<Action>(), "{id}");
		ResourceState unauthorised = new ResourceState(ENTITY_NAME, "INAU", new HashSet<Action>(), "unauthorised/{id}");
		ResourceState authorised = new ResourceState(ENTITY_NAME, "LIVE", new HashSet<Action>(), "authorised/{id}");
		ResourceState reversed = new ResourceState(ENTITY_NAME, "RNAU", new HashSet<Action>(), "reversed/{id}");
		ResourceState history = new ResourceState(ENTITY_NAME, "REVE", new HashSet<Action>(), "history/{id}");
		ResourceState end = new ResourceState(ENTITY_NAME, "end", new HashSet<Action>(), "{id}");
	
		begin.addTransition("PUT", unauthorised);
		
		unauthorised.addTransition("PUT", unauthorised);
		unauthorised.addTransition("PUT", authorised);
		unauthorised.addTransition("DELETE", end);
		
		authorised.addTransition("PUT", history);
		
		history.addTransition("PUT", reversed);
		
		
		ResourceStateMachine sm = new ResourceStateMachine(begin);
		assertEquals(6, sm.getStates().size());
		
		Set<ResourceState> testStates = new HashSet<ResourceState>();
		testStates.add(begin);
		testStates.add(unauthorised);
		testStates.add(authorised);
		testStates.add(reversed);
		testStates.add(history);
		testStates.add(end);
		for (ResourceState s : testStates) {
			assertTrue(sm.getStates().contains(s));
		}
		
		System.out.println(HypermediaValidator.createValidator(sm).graph());
	}
	
	/**
	 * Test {@link ResourceStateMachine#getStates() should return all states.}
	 */
	@Test
	public void testGetStates() {
		String ENTITY_NAME = "";
		ResourceState begin = new ResourceState(ENTITY_NAME, "begin", new HashSet<Action>(), "{id}");
		ResourceState exists = new ResourceState(ENTITY_NAME, "exists", new HashSet<Action>(), "{id}");
		ResourceState end = new ResourceState(exists, "end", new HashSet<Action>());
	
		begin.addTransition("PUT", exists);
		exists.addTransition("PUT", exists);
		exists.addTransition("DELETE", end);
		
		ResourceStateMachine sm = new ResourceStateMachine(begin);
		Collection<ResourceState> states = sm.getStates();
		assertEquals("Number of states", 3, states.size());
		assertTrue(states.contains(begin));
		assertTrue(states.contains(exists));
		assertTrue(states.contains(end));
	}

	@Test
	public void testGetTransitions() {
		String ENTITY_NAME = "";
		ResourceState begin = new ResourceState(ENTITY_NAME, "begin", new HashSet<Action>(), "{id}");
		ResourceState exists = new ResourceState(ENTITY_NAME, "exists", new HashSet<Action>(), "{id}");
		ResourceState end = new ResourceState(exists, "end", new HashSet<Action>());
	
		begin.addTransition("PUT", exists);
		exists.addTransition("PUT", exists);
		exists.addTransition("DELETE", end);
		
		ResourceStateMachine sm = new ResourceStateMachine(begin);
		Map<String,Transition> transitions = sm.getTransitionsById();
		assertEquals("Number of transistions", 3, transitions.size());
		assertNotNull(transitions.get(".begin>.exists"));
		assertNotNull(transitions.get(".exists>.exists"));
		assertNotNull(transitions.get(".exists>.end"));
	}
	
	@Test
	public void testInteractionByPath() {
		String ENTITY_NAME = "";
		ResourceState begin = new ResourceState(ENTITY_NAME, "begin", new HashSet<Action>(), "{id}");
		ResourceState exists = new ResourceState(ENTITY_NAME, "exists", new HashSet<Action>(), "{id}");
		ResourceState end = new ResourceState(ENTITY_NAME, "end", new HashSet<Action>(), "{id}");
	
		begin.addTransition("PUT", exists);
		exists.addTransition("PUT", exists);
		exists.addTransition("DELETE", end);
		
		ResourceStateMachine sm = new ResourceStateMachine(begin);

		Map<String, Set<String>> interactionMap = sm.getInteractionByPath();
		assertEquals("Number of resources", 1, interactionMap.size());
		Set<String> entrySet = interactionMap.keySet();
		assertTrue(entrySet.contains("{id}"));
		Collection<String> interactions = interactionMap.get("{id}");
		assertEquals("Number of interactions", 3, interactions.size());
		assertTrue(interactions.contains("GET"));
		assertTrue(interactions.contains("PUT"));
		assertTrue(interactions.contains("DELETE"));
	}

	@Test
	public void testInteractionByPathSingle() {
		String ENTITY_NAME = "";
		ResourceState begin = new ResourceState(ENTITY_NAME, "begin", new HashSet<Action>(), "/root");
		begin.addTransition("GET", begin);

		ResourceStateMachine sm = new ResourceStateMachine(begin);
		
		Map<String, Set<String>> interactionMap = sm.getInteractionByPath();
		assertEquals("Number of resources", 1, interactionMap.size());
		Set<String> entrySet = interactionMap.keySet();
		assertTrue(entrySet.contains("/root"));
		Collection<String> interactions = interactionMap.get("/root");
		assertEquals("Number of interactions", 1, interactions.size());
		assertTrue(interactions.contains("GET"));
	
	}

	@Test
	public void testInteractionByPathPsuedo() {
		String ENTITY_NAME = "";
		ResourceState exists = new ResourceState(ENTITY_NAME, "exists", new HashSet<Action>(), "{id}");
		ResourceState end = new ResourceState(exists, "end", new HashSet<Action>());
	
		exists.addTransition("PUT", exists);
		exists.addTransition("DELETE", end);
		
		ResourceStateMachine sm = new ResourceStateMachine(exists);

		Map<String, Set<String>> interactionMap = sm.getInteractionByPath();
		assertEquals("Number of resources", 1, interactionMap.size());
		Set<String> entrySet = interactionMap.keySet();
		assertTrue(entrySet.contains("{id}"));
		Collection<String> interactions = interactionMap.get("{id}");
		assertEquals("Number of interactions", 3, interactions.size());
		assertTrue(interactions.contains("GET"));
		assertTrue(interactions.contains("PUT"));
		assertTrue(interactions.contains("DELETE"));
	}

	@Test
	public void testInteractionByPathTransient() {
		String ENTITY_NAME = "";
		ResourceState exists = new ResourceState(ENTITY_NAME, "exists", new HashSet<Action>(), "{id}");
		ResourceState deleted = new ResourceState(ENTITY_NAME, "end", new HashSet<Action>(), "{id}");
	
		exists.addTransition("PUT", exists);
		exists.addTransition("DELETE", deleted);
		// auto transition
		deleted.addTransition(exists);
		
		ResourceStateMachine sm = new ResourceStateMachine(exists);

		Map<String, Set<String>> interactionMap = sm.getInteractionByPath();
		assertEquals("Number of resources", 1, interactionMap.size());
		Set<String> entrySet = interactionMap.keySet();
		assertTrue(entrySet.contains("{id}"));
		Collection<String> interactions = interactionMap.get("{id}");
		assertEquals("Number of interactions", 3, interactions.size());
		assertTrue(interactions.contains("GET"));
		assertTrue(interactions.contains("PUT"));
		assertTrue(interactions.contains("DELETE"));
	}

	@Test
	public void testInteractions() {
		String ENTITY_NAME = "";
		ResourceState begin = new ResourceState(ENTITY_NAME, "begin", new HashSet<Action>(), "{id}");
		ResourceState exists = new ResourceState(ENTITY_NAME, "exists", new HashSet<Action>(), "{id}");
		ResourceState end = new ResourceState(ENTITY_NAME, "end", new HashSet<Action>(), "{id}");
	
		begin.addTransition("PUT", exists);
		exists.addTransition("PUT", exists);
		exists.addTransition("DELETE", end);
		
		ResourceStateMachine sm = new ResourceStateMachine(begin);

		Set<String> interactions = sm.getInteractions(begin);
		assertEquals("Number of interactions", 3, interactions.size());
		assertTrue(interactions.contains("GET"));
		assertTrue(interactions.contains("PUT"));
		assertTrue(interactions.contains("DELETE"));
	}

	@Test
	public void testSubstateInteractions() {
		String ENTITY_NAME = "";
  		ResourceState initial = new ResourceState(ENTITY_NAME, "initial", new HashSet<Action>(), "/entity");
		ResourceState published = new ResourceState(ENTITY_NAME, "published", new HashSet<Action>(), "/published");
		ResourceState publishedDeleted = new ResourceState(published, "publishedDeleted", new HashSet<Action>());
		ResourceState draft = new ResourceState(ENTITY_NAME, "draft", new HashSet<Action>(), "/draft");
		ResourceState draftDeleted = new ResourceState(draft, "draftDeleted", new HashSet<Action>());
	
		// create draft
		initial.addTransition("PUT", draft);
		// updated draft
		draft.addTransition("PUT", draft);
		// publish
		draft.addTransition("PUT", published);
		// delete draft
		draft.addTransition("DELETE", draftDeleted);
		// delete published
		published.addTransition("DELETE", publishedDeleted);
		
		ResourceStateMachine sm = new ResourceStateMachine(initial);

		Set<String> initialInteractions = sm.getInteractions(initial);
		assertEquals("Number of interactions", 1, initialInteractions.size());
		assertTrue(initialInteractions.contains("GET"));

		Set<String> draftInteractions = sm.getInteractions(draft);
		assertEquals("Number of interactions", 3, draftInteractions.size());
		assertTrue(draftInteractions.contains("GET"));
		assertTrue(draftInteractions.contains("PUT"));
		assertTrue(draftInteractions.contains("DELETE"));

		Set<String> publishInteractions = sm.getInteractions(published);
		assertEquals("Number of interactions", 3, publishInteractions.size());
		assertTrue(publishInteractions.contains("GET"));
		assertTrue(publishInteractions.contains("PUT"));
		assertTrue(publishInteractions.contains("DELETE"));

		Set<String> deletedInteractions = sm.getInteractions(draftDeleted);
		assertEquals("Number of interactions", 3, deletedInteractions.size());
		assertTrue(deletedInteractions.contains("GET"));
		assertTrue(deletedInteractions.contains("PUT"));
		assertTrue(deletedInteractions.contains("DELETE"));

		Set<String> publishedDeletedInteractions = sm.getInteractions(publishedDeleted);
		assertEquals("Number of interactions", 3, publishedDeletedInteractions.size());
		assertTrue(publishedDeletedInteractions.contains("GET"));
		assertTrue(publishedDeletedInteractions.contains("PUT"));
		assertTrue(publishedDeletedInteractions.contains("DELETE"));

	}

	@Test
	public void testStateByPath() {
		String ENTITY_NAME = "";
  		ResourceState initial = new ResourceState(ENTITY_NAME, "initial", new HashSet<Action>(), "/entity");
		ResourceState published = new ResourceState(initial, "published", new HashSet<Action>(), "/published");
		ResourceState publishedDeleted = new ResourceState(published, "publishedDeleted", new HashSet<Action>());
		ResourceState draft = new ResourceState(initial, "draft", new HashSet<Action>(), "/draft");
		ResourceState draftDeleted = new ResourceState(draft, "draftDeleted", new HashSet<Action>());
	
		// create draft
		initial.addTransition("PUT", draft);
		// updated draft
		draft.addTransition("PUT", draft);
		// publish
		draft.addTransition("PUT", published);
		// delete draft
		draft.addTransition("DELETE", draftDeleted);
		// delete published
		published.addTransition("DELETE", publishedDeleted);
		
		ResourceStateMachine sm = new ResourceStateMachine(initial);

		Map<String, Set<ResourceState>> stateMap = sm.getResourceStatesByPath();
		assertEquals("Number of states", 3, stateMap.size());
		Set<String> entrySet = stateMap.keySet();
		assertTrue(entrySet.contains("/entity"));
		assertTrue(entrySet.contains("/entity/published"));
		assertTrue(entrySet.contains("/entity/draft"));
		assertEquals(1, stateMap.get("/entity").size());
		assertEquals(initial, stateMap.get("/entity").iterator().next());
		assertEquals(2, stateMap.get("/entity/published").size());
		assertTrue(stateMap.get("/entity/published").contains(published));
		assertTrue(stateMap.get("/entity/published").contains(publishedDeleted));
		assertEquals(2, stateMap.get("/entity/draft").size());
		assertTrue(stateMap.get("/entity/draft").contains(draft));
		assertTrue(stateMap.get("/entity/draft").contains(draftDeleted));
	}

	@Test
	public void testStateByPathSingle() {
		String ENTITY_NAME = "";
  		ResourceState initial = new ResourceState(ENTITY_NAME, "initial", new HashSet<Action>(), "/entity");
  		ResourceStateMachine sm = new ResourceStateMachine(initial);
		Map<String, Set<ResourceState>> stateMap = sm.getResourceStatesByPath();
		assertEquals("Number of states", 1, stateMap.size());
		Set<String> entrySet = stateMap.keySet();
		assertTrue(entrySet.contains("/entity"));
	}

	@Test
	public void testStateByPathPseudo() {
		String ENTITY_NAME = "";
  		ResourceState initial = new ResourceState(ENTITY_NAME, "initial", new HashSet<Action>(), "/entity");
		ResourceState draft = new ResourceState(initial, "draft", new HashSet<Action>(), "/draft");
		ResourceState deleted = new ResourceState(initial, "deleted", null);
	
		// create draft
		initial.addTransition("PUT", draft);
		// updated draft
		draft.addTransition("PUT", draft);
		// delete draft
		draft.addTransition("DELETE", deleted);
		// delete entity
		initial.addTransition("DELETE", deleted);
		
		ResourceStateMachine sm = new ResourceStateMachine(initial);

		Map<String, Set<ResourceState>> stateMap = sm.getResourceStatesByPath();
		assertEquals("Number of states", 3, sm.getStates().size());
		assertEquals("Number of real states", 2, stateMap.size());
		Set<String> entrySet = stateMap.keySet();
		assertTrue(entrySet.contains("/entity"));
		assertEquals(2, stateMap.get("/entity").size());
		assertTrue(stateMap.get("/entity").contains(initial));
		assertTrue(stateMap.get("/entity").contains(deleted));
		assertTrue(entrySet.contains("/entity/draft"));
		assertEquals(1, stateMap.get("/entity/draft").size());
		assertTrue(stateMap.get("/entity/draft").contains(draft));
	}

	@Test
	public void testGetResourceStatesByPath() {
		String ENTITY_NAME = "";
  		ResourceState initial = new ResourceState(ENTITY_NAME, "initial", new HashSet<Action>(), "/entity");
		ResourceState published = new ResourceState(initial, "published", new HashSet<Action>(), "/published");
		ResourceState publishedDeleted = new ResourceState(published, "publishedDeleted", new HashSet<Action>());
		ResourceState draft = new ResourceState(initial, "draft", new HashSet<Action>(), "/draft");
		ResourceState draftDeleted = new ResourceState(draft, "draftDeleted", new HashSet<Action>());
	
		// create draft
		initial.addTransition("PUT", draft);
		// updated draft
		draft.addTransition("PUT", draft);
		// publish
		draft.addTransition("PUT", published);
		// delete draft
		draft.addTransition("DELETE", draftDeleted);
		// delete published
		published.addTransition("DELETE", publishedDeleted);
		
		ResourceStateMachine sm = new ResourceStateMachine(initial);

		Map<String, Set<ResourceState>> stateMap = sm.getResourceStatesByPath(draft);
		assertEquals("Number of states", 2, stateMap.size());
		Set<String> entrySet = stateMap.keySet();
		assertTrue(entrySet.contains("/entity/published"));
		assertTrue(entrySet.contains("/entity/draft"));
		assertEquals(2, stateMap.get("/entity/published").size());
		assertTrue(stateMap.get("/entity/published").contains(published));
		assertTrue(stateMap.get("/entity/published").contains(publishedDeleted));
		assertEquals(2, stateMap.get("/entity/draft").size());
		assertTrue(stateMap.get("/entity/draft").contains(draft));
		assertTrue(stateMap.get("/entity/draft").contains(draftDeleted));
	}

	@Test
	public void testGetState() {
		String ENTITY_NAME = "";
  		ResourceState initial = new ResourceState(ENTITY_NAME, "initial", new HashSet<Action>(), "/entity");
		ResourceState published = new ResourceState(initial, "published", new HashSet<Action>(), "/published");
		ResourceState draft = new ResourceState(initial, "draft", new HashSet<Action>(), "/draft");
		ResourceState deleted = new ResourceState(initial, "deleted", new HashSet<Action>());
	
		// create draft
		initial.addTransition("PUT", draft);
		// updated draft
		draft.addTransition("PUT", draft);
		// publish
		draft.addTransition("PUT", published);
		// delete draft
		draft.addTransition("DELETE", deleted);
		// delete published
		published.addTransition("DELETE", deleted);
		
		ResourceStateMachine sm = new ResourceStateMachine(initial);

		assertEquals(2, sm.getResourceStatesForPath(null).size());
		assertTrue(sm.getResourceStatesForPath(null).contains(initial));
		assertTrue(sm.getResourceStatesForPath(null).contains(deleted));
		assertEquals(1, sm.getResourceStatesForPath("/entity/published").size());
		assertTrue(sm.getResourceStatesForPath("/entity/published").contains(published));
		assertEquals(1, sm.getResourceStatesForPath("/entity/draft").size());
		assertTrue(sm.getResourceStatesForPath("/entity/draft").contains(draft));
	}

	@Test(expected=AssertionError.class)
	public void testInteractionsInvalidState() {
		String ENTITY_NAME = "";
		ResourceState begin = new ResourceState(ENTITY_NAME, "begin", new HashSet<Action>(), "{id}");
		ResourceState exists = new ResourceState(ENTITY_NAME, "exists", new HashSet<Action>(), "{id}");
		ResourceState end = new ResourceState(ENTITY_NAME, "end", new HashSet<Action>(), "{id}");
	
		begin.addTransition("PUT", exists);
		exists.addTransition("PUT", exists);
		exists.addTransition("DELETE", end);
		
		ResourceStateMachine sm = new ResourceStateMachine(begin);

		ResourceState other = new ResourceState("other", "initial", new HashSet<Action>(), "/other");
		sm.getInteractions(other);
	}

	@Test
	public void testTransitionToStateMachine() {
		String PROCESS_ENTITY_NAME = "process";
		String TASK_ENTITY_NAME = "task";

		// process behaviour
		ResourceState processes = new ResourceState(PROCESS_ENTITY_NAME, "processes", new HashSet<Action>(), "/processes");
		ResourceState newProcess = new ResourceState(PROCESS_ENTITY_NAME, "new", new HashSet<Action>(), "/new");
		// create new process
		processes.addTransition("POST", newProcess);

		// Process states
		ResourceState processInitial = new ResourceState(PROCESS_ENTITY_NAME, "initialProcess", new HashSet<Action>(), "/processes/{id}");
		ResourceState processStarted = new ResourceState(processInitial, "started", new HashSet<Action>());
		ResourceState nextTask = new ResourceState(PROCESS_ENTITY_NAME,	"taskAvailable", new HashSet<Action>(), "/nextTask");
		ResourceState processCompleted = new ResourceState(processInitial,	"completedProcess", new HashSet<Action>());
		// start new process
		newProcess.addTransition("PUT", processInitial);
		processInitial.addTransition("PUT", processStarted);
		// do a task
		processStarted.addTransition("GET", nextTask);
		// finish the process
		processStarted.addTransition("DELETE", processCompleted);

		ResourceStateMachine processSM = new ResourceStateMachine(processes);

		// Task states
		ResourceState taskAcquired = new ResourceState(TASK_ENTITY_NAME, "acquired", new HashSet<Action>(), "/acquired");
		ResourceState taskComplete = new ResourceState(TASK_ENTITY_NAME, "complete", new HashSet<Action>(), "/completed");
		ResourceState taskAbandoned = new ResourceState(taskAcquired, "abandoned", new HashSet<Action>());
		// abandon task
		taskAcquired.addTransition("DELETE", taskAbandoned);
		// complete task
		taskAcquired.addTransition("PUT", taskComplete);

		ResourceStateMachine taskSM = new ResourceStateMachine(taskAcquired);
		/*
		 * acquire task by a PUT to the initial state of the task state machine (acquired)
		 */
		nextTask.addTransition("PUT", taskSM);

		ResourceState home = new ResourceState("", "home", new HashSet<Action>(), "/");
		home.addTransition("GET", processSM);
		ResourceStateMachine serviceDocumentSM = new ResourceStateMachine(home);
		
		Map<String, Set<String>> interactionMap = serviceDocumentSM.getInteractionByPath();
		assertEquals(7, interactionMap.size());

		// all target states, including states not for this entity (application states)
		Collection<ResourceState> targetStates = serviceDocumentSM.getInitial().getAllTargets();
		assertEquals(1, targetStates.size());
		assertEquals(10, serviceDocumentSM.getStates().size());
	}

	/*
	 * Test we do not return any links if our entity is null (not found).
	 */
	@SuppressWarnings("unchecked")
	@Test
	public void testGetLinksEntityNotFound() {
		ResourceState initial = new ResourceState("NOTE", "initial", new HashSet<Action>(), "/note/{id}");
		
		// the null entity for our test
		EntityResource<Object> testResponseEntity = null;
		
		// initialise and get the application state (links)
		ResourceStateMachine stateMachine = new ResourceStateMachine(initial);
		Collection<Link> links = stateMachine.injectLinks(mock(MultivaluedMap.class), testResponseEntity, initial, null);
		assertNotNull(links);
		assertTrue(links.isEmpty());
	}

	/*
	 * We use links (hypermedia) for controlling / describing application 
	 * state.  Test we return the link to 'self' correctly for our test resource.
	 */
	@SuppressWarnings("unchecked")
	@Test
	public void testGetLinksSelf() {
		String ENTITY_NAME = "NOTE";
		String resourcePath = "/notes/new";
		ResourceState initial = new ResourceState(ENTITY_NAME, "initial", new HashSet<Action>(), resourcePath);
		EntityResource<Object> testResponseEntity = new EntityResource<Object>(null);
		
		// initialise and get the application state (links)
		ResourceStateMachine stateMachine = new ResourceStateMachine(initial);
		Collection<Link> links = stateMachine.injectLinks(mock(MultivaluedMap.class), testResponseEntity, initial, null);

		assertNotNull(links);
		assertFalse(links.isEmpty());
		assertEquals(1, links.size());
		Link link = (Link) links.toArray()[0];
		assertEquals("self", link.getRel());
		assertEquals("/baseuri/notes/new", link.getHref());
		assertEquals("NOTE.initial>NOTE.initial", link.getId());
	}

	/*
	 * We use links (hypermedia) for controlling / describing application 
	 * state.  Test we return the link to 'self' correctly for our test 
	 * resource; in this self link we have used a path parameter.
	 */
	@Test
	public void testGetLinksSelfPathParameters() {
		String ENTITY_NAME = "NOTE";
		String resourcePath = "/notes/{id}/reviewers";
		ResourceState initial = new ResourceState(ENTITY_NAME, "initial", new HashSet<Action>(), resourcePath);
		EntityResource<Object> testResponseEntity = new EntityResource<Object>(null);
		
		/*
		 * Mock the path parameters with the default 'id' element
		 */
		MultivaluedMap<String, String> mockPathparameters = new MultivaluedMapImpl<String>();
		mockPathparameters.add("id", "123");
		
		// initialise and get the application state (links)
		ResourceStateMachine stateMachine = new ResourceStateMachine(initial);
		Collection<Link> links = stateMachine.injectLinks(mockPathparameters, testResponseEntity, initial, null);

		assertNotNull(links);
		assertFalse(links.isEmpty());
		assertEquals(1, links.size());
		Link link = (Link) links.toArray()[0];
		assertEquals("self", link.getRel());
		assertEquals("/baseuri/notes/123/reviewers", link.getHref());
		assertEquals("NOTE.initial>NOTE.initial", link.getId());
	}

	/*
	 * We use links (hypermedia) for controlling / describing application 
	 * state.  Test we return the link to 'self' correctly for our test resource.
	 */
	@Test
	public void testGetLinksSelfTemplate() {
		String ENTITY_NAME = "NOTE";
		String resourcePath = "/notes/{id}";
		ResourceState initial = new ResourceState(ENTITY_NAME, "initial", new HashSet<Action>(), resourcePath);
		EntityResource<Object> testResponseEntity = new EntityResource<Object>(null);
		
		/*
		 * Mock the path parameters with default 'id' path element
		 */
		MultivaluedMap<String, String> mockPathparameters = new MultivaluedMapImpl<String>();
		mockPathparameters.add("id", "123");

		// initialise and get the application state (links)
		ResourceStateMachine stateMachine = new ResourceStateMachine(initial);
		Collection<Link> links = stateMachine.injectLinks(mockPathparameters, testResponseEntity, initial, null);

		assertNotNull(links);
		assertFalse(links.isEmpty());
		assertEquals(1, links.size());
		Link link = (Link) links.toArray()[0];
		assertEquals("self", link.getRel());
		assertEquals("/baseuri/notes/123", link.getHref());
		assertEquals("NOTE.initial>NOTE.initial", link.getId());
	}

	/*
	 * We use links (hypermedia) for controlling / describing application 
	 * state.  Test we return the links to other resource in our state
	 * machine.
	 */
	@SuppressWarnings({ "unchecked" })
	@Test
	public void testGetLinksOtherResources() {
		String rootResourcePath = "/";
		ResourceState initial = new ResourceState("root", "initial", new HashSet<Action>(), rootResourcePath);
		String NOTE_ENTITY = "NOTE";
		String notesResourcePath = "/notes";
		CollectionResourceState notesResource = new CollectionResourceState(NOTE_ENTITY, "collection", new HashSet<Action>(), notesResourcePath);
		String PERSON_ENTITY = "PERSON";
		String personResourcePath = "/persons";
		CollectionResourceState personsResource = new CollectionResourceState(PERSON_ENTITY, "collection", new HashSet<Action>(), personResourcePath);
		
		// create the transitions (links)
		initial.addTransition("GET", notesResource);
		initial.addTransition("GET", personsResource);

		// initialise and get the application state (links)
		ResourceStateMachine stateMachine = new ResourceStateMachine(initial);
		Collection<Link> unsortedLinks = stateMachine.injectLinks(mock(MultivaluedMap.class), new EntityResource<Object>(null), initial, null);

		assertNotNull(unsortedLinks);
		assertFalse(unsortedLinks.isEmpty());
		assertEquals(3, unsortedLinks.size());
		/*
		 * expect 3 links
		 * 'self'
		 * 'collection notes'
		 * 'colleciton persons'
		 */
		List<Link> links = new ArrayList<Link>(unsortedLinks);
		// sort the links so we have a predictable order for this test
		Collections.sort(links, new Comparator<Link>() {
			@Override
			public int compare(Link o1, Link o2) {
				return o1.getId().compareTo(o2.getId());
			}
			
		});
		// notes
		assertEquals("collection", links.get(0).getRel());
		assertEquals("/baseuri/notes", links.get(0).getHref());
		assertEquals("root.initial>NOTE.collection", links.get(0).getId());
		// persons
		assertEquals("collection", links.get(1).getRel());
		assertEquals("/baseuri/persons", links.get(1).getHref());
		assertEquals("root.initial>PERSON.collection", links.get(1).getId());
		// service root
		assertEquals("self", links.get(2).getRel());
		assertEquals("/baseuri/", links.get(2).getHref());
		assertEquals("root.initial>root.initial", links.get(2).getId());
	}

	/*
	 * We use links (hypermedia) for controlling / describing application 
	 * state.  Test we return the links for the collection itself.
	 */
	@SuppressWarnings({ "unchecked" })
	@Test
	public void testGetLinksCollection() {
		String NOTE_ENTITY = "NOTE";
		String notesResourcePath = "/notes";
		CollectionResourceState notesResource = new CollectionResourceState(NOTE_ENTITY, "collection", new HashSet<Action>(), notesResourcePath);
		String noteItemResourcePath = "/notes/{noteId}";
		ResourceState noteResource = new ResourceState(NOTE_ENTITY, "item", new HashSet<Action>(), noteItemResourcePath);
		/* create the transitions (links) */
		// link to form to create new note
		notesResource.addTransition("POST", new ResourceState("stack", "new", new HashSet<Action>(), "/notes/new", "new".split(" ")));
		/*
		 * define transition to view each item of the note collection
		 * no linkage map as target URI element (self) must exist in source entity element (also self)
		 */
		Map<String, String> uriLinkageMap = new HashMap<String, String>();
		notesResource.addTransitionForEachItem("GET", noteResource, uriLinkageMap);
		// the items of the collection
		List<EntityResource<Object>> entities = new ArrayList<EntityResource<Object>>();
		entities.add(new EntityResource<Object>(createTestNote("1")));
		entities.add(new EntityResource<Object>(createTestNote("2")));
		entities.add(new EntityResource<Object>(createTestNote("6")));
		CollectionResource<Object> testResponseEntity = new CollectionResource<Object>("notes", entities);

		// initialise and get the application state (links)
		ResourceStateMachine stateMachine = new ResourceStateMachine(notesResource, new BeanTransformer());
		Collection<Link> unsortedLinks = stateMachine.injectLinks(mock(MultivaluedMap.class), testResponseEntity, notesResource, null);

		assertNotNull(unsortedLinks);
		assertFalse(unsortedLinks.isEmpty());
		assertEquals(2, unsortedLinks.size());
		/*
		 * expect 2 links - self and one to form to create new note
		 */
		List<Link> links = new ArrayList<Link>(unsortedLinks);
		// sort the links so we have a predictable order for this test
		Collections.sort(links, new Comparator<Link>() {
			@Override
			public int compare(Link o1, Link o2) {
				return o1.getId().compareTo(o2.getId());
			}
		});
		// notes resource (self)
		assertEquals("GET", links.get(0).getMethod());
		assertEquals("self", links.get(0).getRel());
		assertEquals("/baseuri/notes", links.get(0).getHref());
		assertEquals("NOTE.collection>NOTE.collection", links.get(0).getId());
		// new notes
		assertEquals("POST", links.get(1).getMethod());
		assertEquals("new", links.get(1).getRel());
		assertEquals("/baseuri/notes/new", links.get(1).getHref());
		assertEquals("NOTE.collection>stack.new", links.get(1).getId());
		
		/* collect the links defined in each entity */
		List<Link> itemLinks = new ArrayList<Link>();
		for (EntityResource<Object> entity : entities) {
			assertNotNull(entity.getLinks());
			itemLinks.addAll(entity.getLinks());
		}
		
		/*
		 * expect 3 links - one to each note for 'collection notes'
		 */
		assertFalse(itemLinks.isEmpty());
		assertEquals(3, itemLinks.size());
		// sort the links so we have a predictable order for this test
		Collections.sort(itemLinks, new Comparator<Link>() {
			@Override
			public int compare(Link o1, Link o2) {
				return o1.getId().compareTo(o2.getId());
			}
			
		});
		// link to note '1'
// TODO with better rel support we should have self and NOTE.item
//		assertEquals("self NOTE.item", links.get(0).getRel());
		assertEquals("item", itemLinks.get(0).getRel());
		assertEquals("/baseuri/notes/1", itemLinks.get(0).getHref());
		assertEquals("NOTE.collection>NOTE.item", itemLinks.get(0).getId());
		// link to note '2'
		assertEquals("item", itemLinks.get(1).getRel());
		assertEquals("/baseuri/notes/2", itemLinks.get(1).getHref());
		assertEquals("NOTE.collection>NOTE.item", itemLinks.get(1).getId());
		// link to note '6'
		assertEquals("item", itemLinks.get(2).getRel());
		assertEquals("/baseuri/notes/6", itemLinks.get(2).getHref());
		assertEquals("NOTE.collection>NOTE.item", itemLinks.get(2).getId());
		
	}

	/*
	 * We use links (hypermedia) for controlling / describing application 
	 * state.  Test we return the links for items in the collection.
	 */
	@SuppressWarnings({ "unchecked" })
	@Test
	public void testGetLinksCollectionItems() {
		String NOTE_ENTITY = "NOTE";
		String notesResourcePath = "/notes";
		CollectionResourceState notesResource = new CollectionResourceState(NOTE_ENTITY, "collection", new HashSet<Action>(), notesResourcePath);
		String noteItemResourcePath = "/notes/{noteId}";
		ResourceState noteResource = new ResourceState(NOTE_ENTITY, "item", new HashSet<Action>(), noteItemResourcePath);
		ResourceState noteFinalState = new ResourceState(NOTE_ENTITY, "final", new HashSet<Action>(), noteItemResourcePath);
		/* create the transitions (links) */
		/*
		 * define transition to view each item of the note collection
		 * no linkage map as target URI element (self) must exist in source entity element (also self)
		 */
		Map<String, String> uriLinkageMap = new HashMap<String, String>();
		notesResource.addTransitionForEachItem("GET", noteResource, uriLinkageMap);
		notesResource.addTransitionForEachItem("DELETE", noteFinalState, uriLinkageMap);
		// the items of the collection
		List<EntityResource<Object>> entities = new ArrayList<EntityResource<Object>>();
		entities.add(new EntityResource<Object>(createTestNote("1")));
		entities.add(new EntityResource<Object>(createTestNote("2")));
		entities.add(new EntityResource<Object>(createTestNote("6")));
		CollectionResource<Object> testResponseEntity = new CollectionResource<Object>("notes", entities);
				
		// initialise and get the application state (links)
		ResourceStateMachine stateMachine = new ResourceStateMachine(notesResource, new BeanTransformer());
		Collection<Link> baseLinks = stateMachine.injectLinks(mock(MultivaluedMap.class), testResponseEntity, notesResource, null);
		// just one link to self, not really testing that here
		assertEquals(1, baseLinks.size());
		
		/* collect the links defined in each entity */
		List<Link> links = new ArrayList<Link>();
		for (EntityResource<Object> entity : entities) {
			assertNotNull(entity.getLinks());
			links.addAll(entity.getLinks());
		}
		
		/*
		 * expect 6 links - one to self for each note for 'collection notes', one to DELETE each note
		 */
		assertFalse(links.isEmpty());
		assertEquals(6, links.size());
		// sort the links so we have a predictable order for this test
		Collections.sort(links, new Comparator<Link>() {
			@Override
			public int compare(Link o1, Link o2) {
				return o1.getId().compareTo(o2.getId());
			}
			
		});
		// link to DELETE note '1'
		assertEquals("final", links.get(0).getRel());
		assertEquals("/baseuri/notes/1", links.get(0).getHref());
		assertEquals("NOTE.collection>NOTE.final", links.get(0).getId());
		assertEquals("DELETE", links.get(0).getMethod());
		// link to DELETE note '2'
		assertEquals("final", links.get(1).getRel());
		assertEquals("/baseuri/notes/2", links.get(1).getHref());
		assertEquals("NOTE.collection>NOTE.final", links.get(1).getId());
		assertEquals("DELETE", links.get(1).getMethod());
		// link to DELETE note '6'
		assertEquals("final", links.get(2).getRel());
		assertEquals("/baseuri/notes/6", links.get(2).getHref());
		assertEquals("NOTE.collection>NOTE.final", links.get(2).getId());
		assertEquals("DELETE", links.get(0).getMethod());
		// link to GET note '1'
		assertEquals("item", links.get(3).getRel());
		assertEquals("/baseuri/notes/1", links.get(3).getHref());
		assertEquals("NOTE.collection>NOTE.item", links.get(3).getId());
		assertEquals("GET", links.get(3).getMethod());
		// link to GET note '2'
		assertEquals("item", links.get(4).getRel());
		assertEquals("/baseuri/notes/2", links.get(4).getHref());
		assertEquals("NOTE.collection>NOTE.item", links.get(4).getId());
		assertEquals("GET", links.get(4).getMethod());
		// link to GET note '6'
		assertEquals("item", links.get(5).getRel());
		assertEquals("/baseuri/notes/6", links.get(5).getHref());
		assertEquals("NOTE.collection>NOTE.item", links.get(5).getId());
		assertEquals("GET", links.get(5).getMethod());
	}

	@SuppressWarnings({ "unused" })
	private Object createTestNote(final String id) {
		return new Object() {
			final String noteId = id;
			public String getNoteId() {
				return noteId;
			}
		};
	}

}
