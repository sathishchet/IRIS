package com.temenos.interaction.loader.properties;

/*
 * #%L
 * interaction-dynamic-loader
 * %%
 * Copyright (C) 2012 - 2016 Temenos Holdings N.V.
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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;

import com.temenos.interaction.core.loader.FileEvent;
import com.temenos.interaction.loader.properties.ReloadablePropertiesFactoryBean.ChangeIndexFileProvider;
import com.temenos.interaction.loader.xml.resource.notification.XmlModificationNotifier;

public class ReloadablePropertiesFactoryBeanTest {

    @Test
    public void testSetListeners() throws Exception {
        List<ReloadablePropertiesListener<Resource>> listeners = new ArrayList<>();
        ReloadablePropertiesListener<Resource> listener1 = mock(ReloadablePropertiesListener.class);
        ReloadablePropertiesListener<Resource> listener2 = mock(ReloadablePropertiesListener.class);
        listeners.add(listener1);
        listeners.add(listener2);
        
        ReloadablePropertiesFactoryBean rp = new ReloadablePropertiesFactoryBean();
        rp.setListeners(listeners);
        
        assertEquals(2, rp.getListeners().size());
        assertTrue(rp.getListeners().contains(listener1));
        assertTrue(rp.getListeners().contains(listener2));
    }

    @Test
    public void testGetListeners() throws Exception {
        List<ReloadablePropertiesListener<Resource>> listeners = new ArrayList<>();
        ReloadablePropertiesListener<Resource> listener1 = mock(ReloadablePropertiesListener.class);
        ReloadablePropertiesListener<Resource> listener2 = mock(ReloadablePropertiesListener.class);
        listeners.add(listener1);
        listeners.add(listener2);
        
        ReloadablePropertiesFactoryBean rp = new ReloadablePropertiesFactoryBean();

        assertTrue(rp.getListeners().isEmpty());

        rp.setListeners(listeners);
        
        assertEquals(2, rp.getListeners().size());
        assertTrue(rp.getListeners().contains(listener1));
        assertTrue(rp.getListeners().contains(listener2));
    }
    
    @Test
    public void testSetProperties() throws Exception {
        Properties properties = new Properties();
        properties.put(1, "one");
        properties.put(2, "two");

        ReloadablePropertiesFactoryBean rp = new ReloadablePropertiesFactoryBean();
        rp.setProperties(properties);
        
        assertEquals(2, rp.getProperties().size());
        assertTrue(rp.getProperties().contains("one"));
        assertTrue(rp.getProperties().containsKey(1));
        assertEquals("one", rp.getProperties().get(1));
        assertTrue(rp.getProperties().contains("two"));
        assertTrue(rp.getProperties().containsKey(2));
        assertEquals("two", rp.getProperties().get(2));
    }

    @Test
    public void testCreateInstance() throws Exception {
        ReloadablePropertiesFactoryBean rp = new ReloadablePropertiesFactoryBean();
        assertTrue(rp.isSingleton());

        ApplicationContext ctx = mock(ApplicationContext.class);
        Resource[] resources = new Resource[0];
        when(ctx.getResources(any(String.class))).thenReturn(resources);
        rp.setApplicationContext(ctx);

        Properties properties = new Properties();
        properties.put(1, "one");
        properties.put(2, "two");
        rp.setProperties(properties);

        List<ReloadablePropertiesListener<Resource>> listeners = new ArrayList<>();
        ReloadablePropertiesListener<Resource> listener1 = mock(ReloadablePropertiesListener.class);
        ReloadablePropertiesListener<Resource> listener2 = mock(ReloadablePropertiesListener.class);
        listeners.add(listener1);
        listeners.add(listener2);
        rp.setListeners(listeners);
        
        String[] patterns = { "*" };
        when(listener1.getResourcePatterns()).thenReturn(patterns);
        when(listener2.getResourcePatterns()).thenReturn(patterns);
        
        XmlModificationNotifier xmlNotifier = mock(XmlModificationNotifier.class);
        rp.setXmlNotifier(xmlNotifier);

        ReloadablePropertiesBase newInstance = (ReloadablePropertiesBase) rp.createInstance();
        assertEquals(rp.getProperties(), newInstance.getProperties());
        assertEquals(rp.getListeners(), newInstance.getListeners());
    }

    @Test(expected=RuntimeException.class)
    public void testCreateInstanceNotSingleton() throws Exception {
        ReloadablePropertiesFactoryBean rp = new ReloadablePropertiesFactoryBean();
        rp.setSingleton(false);
        rp.createInstance();
    }

    @Test
    public void testReload() throws Exception {
        ReloadablePropertiesFactoryBean rp = new ReloadablePropertiesFactoryBean();

        String rootPath = "models-gen/src/generated/iris";
        Path root = Paths.get(rootPath);
        Files.createDirectories(root);
        
        Path tmp1 = Paths.get(rootPath + "/metadata-customer.xml");
        if(!Files.exists(tmp1)) Files.createFile(tmp1);
        
        Path tmp2 = Paths.get(rootPath + "/IRIS-customer.properties");
        if(!Files.exists(tmp2)) Files.createFile(tmp2);

        Resource[] resources = new Resource[2];
        Resource resource1 = mock(Resource.class);
        when(resource1.getURI()).thenReturn(tmp1.toUri());
        when(resource1.getURL()).thenReturn(tmp1.toUri().toURL());
        Resource resource2 = mock(Resource.class);
        when(resource2.getURI()).thenReturn(tmp2.toUri());
        when(resource2.getURL()).thenReturn(tmp2.toUri().toURL());
        resources[0] = resource1;
        resources[1] = resource2;

        ApplicationContext ctx = mock(ApplicationContext.class);
        when(ctx.getResources(any(String.class))).thenReturn(resources);

        rp.setApplicationContext(ctx);

        // set last modified time of the lastChange file to the maximum long
        // so that the class believes that new changes should be processed
        Path lastChange = Paths.get("models-gen/lastChange");
        if(Files.exists(lastChange)) Files.delete(lastChange);
        Files.createFile(lastChange);
        // add the files file to the lastChange file index
        byte[] tmp1FileNameBytes = (tmp1.toString() + System.getProperty("line.separator")).getBytes();
        Files.write(lastChange, tmp1FileNameBytes, StandardOpenOption.APPEND);
        byte[] tmp2FileNameBytes = tmp2.toString().getBytes();
        Files.write(lastChange, tmp2FileNameBytes, StandardOpenOption.APPEND);
        // set the modified time for one year ahead
        Files.setAttribute(lastChange, "lastModifiedTime", FileTime.fromMillis(System.currentTimeMillis() + 31536000000L));
        
        XmlModificationNotifier xmlNotifier = new XmlModificationNotifier();
        XmlModificationNotifier spy = Mockito.spy(xmlNotifier);
        Mockito.doNothing().when(spy).execute(any(FileEvent.class));
        rp.setXmlNotifier(spy);
        
        // this is the way to create an instance of the private variable reloadableProperties...
        rp.createInstance();
        
        rp.reload(false);
        
        verify(spy).execute(any(FileEvent.class));
        // TODO: we should verify that the properties are reloaded, but currently
        // there's no way to do this...
    }

    @Test
    public void testReloadForced() throws Exception {
        ReloadablePropertiesFactoryBean rp = new ReloadablePropertiesFactoryBean();

        String rootPath = "models-gen/src/generated/iris";
        Path root = Paths.get(rootPath);
        Files.createDirectories(root);
        
        Path tmp1 = Paths.get(rootPath + "/metadata-customer.xml");
        if(!Files.exists(tmp1)) Files.createFile(tmp1);
        
        Path tmp2 = Paths.get(rootPath + "/IRIS-customer.properties");
        if(!Files.exists(tmp2)) Files.createFile(tmp2);

        Resource[] resources = new Resource[2];
        Resource resource1 = mock(Resource.class);
        when(resource1.getURI()).thenReturn(tmp1.toUri());
        when(resource1.getURL()).thenReturn(tmp1.toUri().toURL());
        Resource resource2 = mock(Resource.class);
        when(resource2.getURI()).thenReturn(tmp2.toUri());
        when(resource2.getURL()).thenReturn(tmp2.toUri().toURL());
        resources[0] = resource1;
        resources[1] = resource2;

        ApplicationContext ctx = mock(ApplicationContext.class);
        when(ctx.getResources(any(String.class))).thenReturn(resources);

        rp.setApplicationContext(ctx);

        // set last modified time of the lastChange file to 0
        // so that the class only reloads if it is forced to
        Path lastChange = Paths.get("models-gen/lastChange");
        if(Files.exists(lastChange)) Files.delete(lastChange);
        Files.createFile(lastChange);
        // add the files file to the lastChange file index
        byte[] tmp1FileNameBytes = (tmp1.toString() + System.getProperty("line.separator")).getBytes();
        Files.write(lastChange, tmp1FileNameBytes, StandardOpenOption.APPEND);
        byte[] tmp2FileNameBytes = tmp2.toString().getBytes();
        Files.write(lastChange, tmp2FileNameBytes, StandardOpenOption.APPEND);
        // set the modified time to 0
        Files.setAttribute(lastChange, "lastModifiedTime", FileTime.fromMillis(0));
        
        XmlModificationNotifier xmlNotifier = new XmlModificationNotifier();
        XmlModificationNotifier spy = Mockito.spy(xmlNotifier);
        Mockito.doNothing().when(spy).execute(any(FileEvent.class));
        rp.setXmlNotifier(spy);
        
        // here we force the reload
        rp.createInstance();
        
        verify(spy).execute(any(FileEvent.class));
        // TODO: we should verify that the properties are reloaded, but currently
        // there's no way to do this...
    }
    
    @Test
    public void testReloadCacheFileUtil(){
        ReloadablePropertiesFactoryBean rp = new ReloadablePropertiesFactoryBean();
        
        //check location returned for application (app-test1-iris)
        ChangeIndexFileProvider changeLocationPresent = rp.new ChangeIndexFileProvider(
                "app-test1-iris=/app/test1/iris/lastChange,app-test2-iris=/app/test2/iris/lastChange","app-test1-iris");
        assertNotNull(changeLocationPresent.getChangeIndexFile());
        assertTrue(changeLocationPresent.getChangeIndexFile().toString().endsWith(Paths.get("/app/test1/iris/lastChange").toString()));
        
        //check null returned as location for the application not provided in configuration (app-test3-iris)
        ChangeIndexFileProvider changeLocationAbsent = rp.new ChangeIndexFileProvider(
                "app-test1-iris=/app/test1/iris/lastChange,app-test2-iris=/app/test2/iris/lastChange","app-test3-iris");
        assertNull(changeLocationAbsent.getChangeIndexFile());
        
        //check default location is provided if the config is missing
        ChangeIndexFileProvider changeLocationDefault = rp.new ChangeIndexFileProvider(
                "\\workspace\\test-app\\lastChange,app-test2-iris=/app/test2/iris/lastChange","app-test3-iris");
        assertNotNull(changeLocationDefault.getChangeIndexFile());
        assertTrue(changeLocationDefault.getChangeIndexFile().toString().endsWith(Paths.get("workspace\\test-app\\lastChange").toString()));
        
        //OLD: check default location alone is provided
        ChangeIndexFileProvider changeLocationOnlyDefault = rp.new ChangeIndexFileProvider(
                "\\workspace\\test-app\\lastChange","app-test3-iris");
        assertNotNull(changeLocationOnlyDefault.getChangeIndexFile());
        assertTrue(changeLocationOnlyDefault.getChangeIndexFile().toString().endsWith(Paths.get("workspace\\test-app\\lastChange").toString()));
        
        //check default returned if the config is having invalid value
        ChangeIndexFileProvider changeLocationInvalid = rp.new ChangeIndexFileProvider(
                "\\workspace\\test-app\\lastChange,app-test2-iris= ","app-test2-iris");
        assertNotNull(changeLocationInvalid.getChangeIndexFile());
        assertTrue(changeLocationInvalid.getChangeIndexFile().toString().endsWith(Paths.get("workspace\\test-app\\lastChange").toString()));
    }

}
