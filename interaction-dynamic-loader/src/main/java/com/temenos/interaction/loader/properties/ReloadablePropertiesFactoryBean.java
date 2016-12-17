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
import java.io.IOError;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
	private String changeIndexLocations;

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

    public String getChangeIndexLocations() {
        return changeIndexLocations;
    }

    public void setChangeIndexLocations(String changeIndexLocations) {
        this.changeIndexLocations = changeIndexLocations;
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
		
		if(changeIndexLocations != null) {		  
		   
    		File irisChangeIndexFile = new ChangeIndexFileProvider(changeIndexLocations, ctx.getApplicationName()).getChangeIndexFile();
    		
    		if(irisChangeIndexFile != null && irisChangeIndexFile.exists()) {
    		    lastChangeFile = irisChangeIndexFile;
    		    logger.info("The following index file will be used for refreshing resources: " + irisChangeIndexFile.getAbsolutePath());
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
	        logger.info(changedPaths.size() + " resources reloaded in " + (System.currentTimeMillis() - initTimestamp) + " ms.");
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

    class ReloadablePropertiesImpl extends ReloadablePropertiesBase implements ReconfigurableBean {
		private static final long serialVersionUID = -3401718333944329073L;

		@Override
		public void reloadConfiguration() throws Exception {
			ReloadablePropertiesFactoryBean.this.reload(false);
		}
	}
    
    /**
     * ChangeIndexFileProvider to get the File which track changes added to models at run-time
     *
     */
    class ChangeIndexFileProvider {
        private Map<String, String> props = new HashMap<String, String>();
        private String appName;
        
        /**
         * <p>instantiate's ChangeIndexFileProvider with args fileLocations and application-name</p>
         * <p>
         * fileLocations sample values
         * "app-iris=/workspace/app-iris/lastChange,app2-iris=/workspace/app2-iris/lastChange,/default/lastChange"
         * </p> 
         * @param fileLocations application name and its change file location
         * @param appName context Application Name
         */
        public ChangeIndexFileProvider(String fileLocations, String appName) {
            this.appName = appName;
            if (StringUtils.isNotBlank(fileLocations)) {
                for (String line : StringUtils.split(fileLocations, ',')) {
                    String[] prop = line.split("=");
                    if (prop.length == 1) {
                        logger.info("Default location provided: " + line);
                        props.put(null, prop[0]);
                        continue;
                    } else if (prop.length != 2) {
                        logger.info("Invalid location provided: " + line);
                        continue;
                    }
                    props.put(prop[0], prop[1]);
                }
            }
        }

        /**
         * Returns the {@link File} associated for the current application from the change locations provided
         * and returns <code>null</code> if not provided
         * @return change index file for the application
         */
        public File getChangeIndexFile() {
            appName = removeSlash(appName);
            //Check if app-name and corresponding location is provided, if not return default location present
            if (StringUtils.isBlank(appName) || 
                    StringUtils.isBlank(props.get(appName)) ? StringUtils.isBlank(props.get(appName = null)) : false)
                return null;
            String filePath = props.get(appName);
            File targetFile = null;
            try{
                targetFile = Paths.get(filePath).toAbsolutePath().toFile();
            }catch(IOError | Exception e){
                logger.warn("Unexpected failure when getting dynamic path ", e);
            }
            return targetFile;
        }
        
        /**
         * <p>Remove slash if present and returns application name.</p>
         * <pre> 
         * removeSlash("\app-name") = "app-name"
         * removeSlash("app-name") = "app-name"
         * </pre>
         * @param appName application name with slash
         * @return application name
         */
        private String removeSlash(String appName){
            if(StringUtils.isNotEmpty(appName) && !StringUtils.isAlphanumeric(String.valueOf(appName.charAt(0)))){
                appName=appName.substring(1);
            }
            return appName;
        }
    }

	@Override
	public void setApplicationContext(ApplicationContext ctx) throws BeansException {
		this.ctx = ctx;
	}
}
