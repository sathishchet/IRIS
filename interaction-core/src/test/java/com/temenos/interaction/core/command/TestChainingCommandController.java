package com.temenos.interaction.core.command;

/*
 * #%L
 * interaction-core
 * %%
 * Copyright (C) 2012 - 2015 Temenos Holdings N.V.
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

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.startsWith;
import static org.mockito.Mockito.when;

import java.util.Arrays;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

/**
 * Test class for the ChainingCommandController {@link ChainingCommandController}
 *
 * @author trojanbug
 */
 
@RunWith(MockitoJUnitRunner.class)
public class TestChainingCommandController {

	@InjectMocks
	private ChainingCommandController controller;
	
	@Mock
	private CommandController commandController;
	
	@Before
	public void setUp() throws Exception {
		when(this.commandController.isValidCommand(startsWith("testCommand"))).thenReturn(true);
		when(this.commandController.fetchCommand(startsWith("testCommand"))).thenReturn(new TestCommand());
		this.controller.setCommandControllers(Arrays.asList(new CommandController[]{
			this.commandController
		}));
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testFetchCommand() {
		assertThat(this.controller.fetchCommand("make-it-fail"), notNullValue());
	}
	
	@Test
	public void testFetchCommandWithInvalidCommandName(){
		assertThat(this.controller.fetchCommand("notAValidCommand"), nullValue());
	}

	@Test
	public void testIsValidCommand() {
		assertThat(this.controller.isValidCommand("testCommand1"), equalTo(true));
	}
	
	@Test
	public void testIsValidCommandWithInvalidCommandName() {
		assertThat(this.controller.isValidCommand("notAValidCommand"), equalTo(false));
	}
	
}
