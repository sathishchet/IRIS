package com.temenos.interaction.loader.properties;

/*
 * #%L
 * interaction-springdsl
 * %%
 * Copyright (C) 2012 - 2014 Temenos Holdings N.V.
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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import org.apache.commons.lang.StringUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.config.PropertiesFactoryBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.util.DefaultPropertiesPersister;
import org.springframework.util.PropertiesPersister;

import com.temenos.interaction.loader.xml.XmlChangedEventImpl;
import com.temenos.interaction.loader.xml.resource.notification.XmlModificationNotifier;
import com.temenos.interaction.springdsl.DynamicProperties;

/**
 * A properties factory bean that creates a reconfigurable Properties object.
 * When the Properties' reloadConfiguration method is called, and the file has
 * changed, the properties are read again from the file. Credit to:
 * http://www.wuenschenswert.net/wunschdenken/archives/127
 */
public class ReloadablePropertiesFactoryBean extends PropertiesFactoryBean implements DynamicProperties,
		DisposableBean, ApplicationContextAware {
	private ApplicationContext ctx;

	private List<ReloadablePropertiesListener<Resource>> preListeners = new ArrayList<>();
	private PropertiesPersister propertiesPersister = new DefaultPropertiesPersister();
	private ReloadablePropertiesBase reloadableProperties;
	private Properties properties;
	private long lastFileTimeStamp = 0;
	private List<Resource> resourcesPath;
	private File lastChangeFile;
	private XmlModificationNotifier xmlNotifier;
	private String customPath;
	private String customFile;
	private String customMapping;

	public void setListeners(List<ReloadablePropertiesListener<Resource>> listeners) {
	    preListeners.addAll(listeners);
	}
	
	List<ReloadablePropertiesListener<Resource>> getListeners() {
	    return this.preListeners;
	}

	@Override
	public void setProperties(Properties properties) {
		this.properties = properties;
	}
	
	Properties getProperties() {
	    return this.properties;
	}

	@Override
	protected Object createInstance() throws IOException {
		// would like to uninherit from AbstractFactoryBean (but it's final!)
		if (!isSingleton())
			throw new RuntimeException("ReloadablePropertiesFactoryBean only works as singleton");
		reloadableProperties = new ReloadablePropertiesImpl();
		reloadableProperties.setProperties(properties);

		if (preListeners != null)
			reloadableProperties.setListeners(preListeners);
		reload(true);
		return reloadableProperties;
	}

	public void setXmlNotifier(XmlModificationNotifier xmlNotifier) {
		this.xmlNotifier = xmlNotifier;
	}

	@Override
	public void destroy() throws Exception {
		reloadableProperties = null;
	}

	protected void reload(boolean forceReload) throws IOException {
		long l = System.currentTimeMillis();
		
		reloadNew(forceReload);
		
		l = System.currentTimeMillis() - l;
		if (l > 2000) {
			logger.warn("Reload time " + l + " ms.");
		}
	}

    /*
     * Collects all resources in the iris directory. It also creates or
     * gets the lastChange file for getting later its modified time.
     * 
     * @return a list of all the files present in the directory
     * models-gen/src/generated/iris
     */
	private List<Resource> initializeResourcesPath() throws IOException {
	    assert ctx != null;
		List<Resource> ret = new ArrayList<>();
		List<Resource> tmp = Arrays.asList(ctx.getResources("classpath*:"));
		for (Resource oneResource : tmp) {
			String sPath = oneResource.getURI().getPath().replace('\\', '/');
			int pos = sPath.indexOf("models-gen/src/generated/iris");
			if (pos > 0) {
				ret.add(oneResource);
				/*
				 * now let's look at the lastChange file
				 */
				String sRoot = sPath.substring(0, pos + "models-gen".length());
				File f = new File(sRoot, "lastChange");
				if (f.exists()) {
					lastChangeFile = f;
				}
				else f.createNewFile();
			}
		}
		
		String irisCacheIndexFileStr = System.getProperty("iris.cache.index.file");				
		
		if(irisCacheIndexFileStr != null) {		    
    		File irisCacheIndexFile = new File(irisCacheIndexFileStr).getAbsoluteFile();
    		
    		if(irisCacheIndexFile.exists()) {
    		    lastChangeFile = irisCacheIndexFile;
    		    logger.info("The following index file will be used for refreshing resources: " + irisCacheIndexFile.getAbsolutePath());
    		}
		}
		
        // Check if Custom Path is available for look-up
        if (customPath != null && customFile != null) {
            File destFile = null;
            String appName = ctx.getApplicationName();
            destFile = Paths.get(customPath, appName, customFile).toAbsolutePath().toFile();
            //Prioritize cross app mapping 
            if (customMapping != null) {
                Path overrideMapping = Paths.get(customPath, customMapping).toAbsolutePath();
                if (Files.exists(overrideMapping) && overrideMapping.toString().endsWith(".properties")) {
                    Properties prop = new Properties();
                    prop.load(Files.newInputStream(overrideMapping));
                    //currently using one-to-one mapping
                    String appCross = (String) prop.get(appName.substring(1));
                    if(StringUtils.isNotEmpty(appCross)){
                        destFile = Paths.get(customPath, appCross, customFile).toAbsolutePath().toFile();
                    }
                }
            }
            if(destFile != null ){
                lastChangeFile = destFile;
                logger.info("Custom Path for refreshing resources: " + destFile.getAbsolutePath() + " , App: "
                        + appName);
                //Force create to listen first time changes after application start
                if(!destFile.exists()){
                    destFile.getParentFile().mkdirs();
                    destFile.createNewFile();
                }
            }
        }

		return ret;
	}

	private List<Resource> getLastChangeAndClear(File f) {
		File lastChangeFileLock = new File(f.getParent(), ".lastChangeLock");
		List<Resource> ret = new ArrayList<>();
        /*
         * Maintain a specific lock to avoid partial file locking.
         */
        try (FileChannel fcLock = new RandomAccessFile(lastChangeFileLock, "rw").getChannel()) {

            try (FileLock lock = fcLock.lock()) {

                try (FileChannel fc = new RandomAccessFile(f, "rws").getChannel()) {

                    try (BufferedReader bufR = new BufferedReader(new FileReader(f))) {
                        String sLine = null;
                        boolean bFirst = true;
                        while ((sLine = bufR.readLine()) != null) {
                            if (bFirst) {
                                if (sLine.startsWith("RefreshAll")) {
                                    ret = null;
                                    break;
                                }
                                bFirst = false;
                            }
                            Resource toAdd = new FileSystemResource(new File(sLine));
                            if (!ret.contains(toAdd)) {
                                ret.add(toAdd);
                            }
                        }
                        /*
                         * Empty the file
                         */
                        fc.truncate(0);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Failed to get the lastChanges contents.", e);
        }
		
		return ret;
	}

	protected void reloadNew(boolean forceReload) throws IOException {

		if (resourcesPath == null) {
			/*
			 * initiate it once for all.
			 */
			resourcesPath = initializeResourcesPath();
		}

		/*
		 * Let's do it as we could miss a file being modified during the scan.
		 */
		boolean reload = false;

		List<Resource> changedPaths = new ArrayList<>();
	    
	    if (lastChangeFile != null && lastChangeFile.exists()) {
	        if(!forceReload) {
	            long lastChange = lastChangeFile.lastModified();
	            if (lastChange <= lastFileTimeStamp) {
	                return;
	            }
	        }
			if (lastChangeFile.length() > 0) {
			    reload = true;
				/*
				 * Mhh, there is something in it ! So we get the lock, read the
				 * contents, and update only the resources in this file. If the
				 * contents starts with "RefreshAll" (without the quotes), then
				 * just look at the timestamp of all resources.
				 * 
				 * @see com.odcgroup.workbench.generation.cartridge.ng.
				 * SimplerEclipseResourceFileSystemNotifier
				 */
				List<Resource> lastChangeFileContents = getLastChangeAndClear(lastChangeFile);
				if (lastChangeFileContents != null) {
					/*
					 * If null, this means the file was starting with
					 * "RefreshAll" (see previous comment)
					 */
					changedPaths = lastChangeFileContents;
				}
			}
			lastFileTimeStamp = lastChangeFile.lastModified();
		} else {
			/*
			 * We do not have the file (yet) (old EDS ?), so use the old
			 * strategy
			 * 
			 * Some file systems (FAT, NTFS) do have a write time resolution of
			 * 2 seconds see
			 * http://msdn.microsoft.com/en-us/library/ms724290%28VS.85%29.aspx
			 * So better give a 2 seconds latency.
			 */
			lastFileTimeStamp = System.currentTimeMillis() - 2000;
		}

		if(!reload)
		    return;
		
		long initTimestamp = System.currentTimeMillis();
		
		refreshResources(changedPaths);
		
	    if (!changedPaths.isEmpty()) {
	        logger.info("App: "+ctx.getApplicationName()+" => "+changedPaths.size() + " resources reloaded in " + (System.currentTimeMillis() - initTimestamp) + " ms.");
	    }
	}
	
	private void refreshResources(List<Resource> resources) {
	    assert propertiesPersister != null;
	    assert reloadableProperties != null;
		for (Resource location : resources) {
			try {
				String fileName = location.getFilename().toLowerCase();

                if (fileName.startsWith("metadata-") && fileName.endsWith(".xml")) {
                    logger.info("Refreshing : " + location.getFilename());
                    if(xmlNotifier != null)
                        xmlNotifier.execute(new XmlChangedEventImpl(location));
                }
				
				if (fileName.endsWith(".properties")) {
					Properties newProperties = new Properties();
					/*
					 * Ensure this property has been loaded.
					 */
					propertiesPersister.load(newProperties, location.getInputStream());
					
					boolean loadNewProperties = false;
					// only update IRIS properties -- ignore all others
					if(fileName.startsWith("iris-")) {
					    loadNewProperties = reloadableProperties.updateProperties(newProperties);
					}
					
					if(loadNewProperties) {
                        logger.info("Loading new : " + location.getFilename());
                        reloadableProperties.notifyPropertiesLoaded(location, newProperties);
                    } else {
                        logger.info("Refreshing : " + location.getFilename());
                        /*
                         * Notify subscribers that properties have been modified
                         */
                        reloadableProperties.notifyPropertiesChanged(location, newProperties);
                    }
				}
			} catch (Exception e) {
				logger.error("Unexpected error when dynamically loading resources ", e);
			}
		}
	}

    public String getCustomPath() {
        return customPath;
    }

    public void setCustomPath(String customPath) {
        this.customPath = customPath;
    }
    
    public String getCustomFile() {
        return customFile;
    }

    public void setCustomFile(String customFile) {
        this.customFile = customFile;
    }

    public String getCustomMapping() {
        return customMapping;
    }

    public void setCustomMapping(String customMapping) {
        this.customMapping = customMapping;
    }

    class ReloadablePropertiesImpl extends ReloadablePropertiesBase implements ReconfigurableBean {
		private static final long serialVersionUID = -3401718333944329073L;

		@Override
		public void reloadConfiguration() throws Exception {
			ReloadablePropertiesFactoryBean.this.reload(false);
		}
	}

	@Override
	public void setApplicationContext(ApplicationContext ctx) throws BeansException {
		this.ctx = ctx;
	}
	
}
