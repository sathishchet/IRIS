package com.temenos.interaction.media.odata.xml.atom;

/*
 * #%L
 * interaction-media-odata-xml
 * %%
 * Copyright (C) 2012 - 2013 Temenos Holdings N.V.
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


import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDateTime;
import org.odata4j.core.OAtomEntity;
import org.odata4j.core.OAtomStreamEntity;
import org.odata4j.core.ODataConstants;
import org.odata4j.core.OEntities;
import org.odata4j.core.OEntity;
import org.odata4j.core.OLink;
import org.odata4j.core.OProperty;
import org.odata4j.edm.EdmEntitySet;
import org.odata4j.edm.EdmProperty;
import org.odata4j.format.FormatWriter;
import org.odata4j.format.xml.XmlFormatWriter;
import org.odata4j.internal.InternalUtil;
import org.odata4j.producer.EntityResponse;
import org.odata4j.stax2.QName2;
import org.odata4j.stax2.XMLFactoryProvider2;
import org.odata4j.stax2.XMLWriter2;

import com.temenos.interaction.core.hypermedia.Link;
import com.temenos.interaction.core.hypermedia.ResourceState;

/**
 * Slightly modified version of @link{org.odata4j.format.xml.AtomEntryFormatWriter} that 
 * is more aligned with JAX-RS.
 * @author aphethean
 *
 */
public class AtomEntryFormatWriter extends XmlFormatWriter implements FormatWriter<EntityResponse> {

	private ResourceState serviceDocument;
	
	public AtomEntryFormatWriter(ResourceState serviceDocument) {
		this.serviceDocument = serviceDocument;
	}

  @Override
  public String getContentType() {
    return ODataConstants.APPLICATION_ATOM_XML_CHARSET_UTF8;
  }

  public void write(UriInfo uriInfo, Writer w, EntityResponse target, EdmEntitySet entitySet, List<OLink> olinks) {
	String baseUri = AtomXMLProvider.getBaseUri(serviceDocument, uriInfo);

    DateTime utc = new DateTime().withZone(DateTimeZone.UTC);
    String updated = InternalUtil.toString(utc);

    XMLWriter2 writer = XMLFactoryProvider2.getInstance().newXMLWriterFactory2().createXMLWriter(w);
    writer.startDocument();

    writer.startElement(new QName2("entry"), atom);
    writer.writeNamespace("m", m);
    writer.writeNamespace("d", d);
    writer.writeAttribute("xml:base", baseUri);

    // this darn writer sometimes uses the EdmEntitySet we pass in and sometimes uses OEntity.getEntitySet
    OEntity origOE = target.getEntity() ;   
    OEntity newOE = OEntities.create(entitySet, origOE.getEntityKey(), origOE.getProperties(), olinks);

    writeEntry(writer, newOE, newOE.getProperties(), newOE.getLinks(), baseUri, updated, entitySet, true);
    writer.endDocument();
  }

  @Override
  public String writeEntry(XMLWriter2 writer, OEntity oe,
	      List<OProperty<?>> entityProperties, List<OLink> entityLinks,
	      String baseUri, String updated,
	      EdmEntitySet ees, boolean isResponse) {
	  return writeEntry(writer, oe, entityProperties, entityLinks, baseUri, updated, ees, isResponse, null);
  }
  
  //OData olink doesn't have option to provide linkid, so writing linkid explicitly 
  public String writeEntry(XMLWriter2 writer, OEntity oe,
	      List<OProperty<?>> entityProperties, List<OLink> entityLinks,
	      String baseUri, String updated,
	      EdmEntitySet ees, boolean isResponse, Collection<Link> linkId) {

	    String relid = null;
	    String absid = null;
	    if (isResponse) {
	      relid = InternalUtil.getEntityRelId(oe);
	      //Odata 4j creates IDs with an L suffix on Edm.Int types, quotes on Edm.String types - remove to conform to interaction links
	      List<String> keys = oe.getEntityType().getKeys();
	      if(keys.size() > 0) {
		      EdmProperty keyProperty = oe.getEntityType().findDeclaredProperty(keys.get(0));
		      if(keyProperty.getType().getFullyQualifiedTypeName().startsWith("Edm.Int") && relid.endsWith("L)")) {
		    	  relid = relid.substring(0, relid.length()-2) + ")";
		      }
	      }
	      absid = (!baseUri.endsWith("/") ? baseUri + "/" : baseUri) + relid;
	      writeElement(writer, "id", absid);
	    }

	    OAtomEntity oae = getAtomInfo(oe);

	    writeElement(writer, "title", oae.getAtomEntityTitle(), "type", "text");
	    String summary = oae.getAtomEntitySummary();
	    if (summary != null) {
	      writeElement(writer, "summary", summary, "type", "text");
	    }

	    LocalDateTime updatedTime = oae.getAtomEntityUpdated();
	    if (updatedTime != null) {
	      updated = InternalUtil.toString(updatedTime.toDateTime(DateTimeZone.UTC));
	    }
	    writeElement(writer, "updated", updated);

	    writer.startElement("author");
	    writeElement(writer, "name", oae.getAtomEntityAuthor());
	    writer.endElement("author");

	    if (entityLinks != null) {
	      if (isResponse) {
	        // the producer has populated the link collection, we just write what he gave us.
	        Collection<Link> linkIdUsed = new HashSet<>();
	        for (OLink link : entityLinks) {
	          String type = (link.isCollection())
	              ? atom_feed_content_type
	              : atom_entry_content_type;
	          String href = link.getHref();
	          if (link.isInline()) {
	            writer.startElement("link");
	            writer.writeAttribute("rel", link.getRelation());
	            if (!"self".equals(link.getRelation()) &&
	            		!"edit".equals(link.getRelation())) {
		            writer.writeAttribute("type", type);
	            }
	            writer.writeAttribute("title", link.getTitle());
	            writer.writeAttribute("href", href);
	            // write the inlined entities inside the link element
	            writeLinkInline(writer, link,
	                href, baseUri, updated, isResponse);
	            writer.endElement("link");
	          } else {
	              writeLink(writer, link, type, href, linkId, linkIdUsed);
	          }
	        }
	      } else {
	        // for requests we include only the provided links
	        // Note: It seems that OLinks for responses are only built using the
	        // title and OLinks for requests have the additional info in them
	        // alread.  I'm leaving that inconsistency in place for now but this
	        // else and its preceding if could probably be unified.
	        for (OLink olink : entityLinks) {
	          String type = olink.isCollection()
	              ? atom_feed_content_type
	              : atom_entry_content_type;

	          writer.startElement("link");
	          writer.writeAttribute("rel", olink.getRelation());
	          writer.writeAttribute("type", type);
	          writer.writeAttribute("title", olink.getTitle());
	          writer.writeAttribute("href", olink.getHref());
	          if (olink.isInline()) {
	            // write the inlined entities inside the link element
	            writeLinkInline(writer, olink, olink.getHref(),
	                baseUri, updated, isResponse);
	          }
	          writer.endElement("link");
	        }
	      }
	    } // else entityLinks null

	    writeElement(writer, "category", null,
	        // oe is null for creates
	        "term", oe == null ? ees.getType().getFullyQualifiedTypeName() : oe.getEntityType().getFullyQualifiedTypeName(),
	        "scheme", scheme);

	    boolean hasStream = false;
	    if (oe != null) {
	      OAtomStreamEntity stream = oe.findExtension(OAtomStreamEntity.class);
	      if (stream != null) {
	        hasStream = true;
	        writer.startElement("content");
	        writer.writeAttribute("type", stream.getAtomEntityType());
	        writer.writeAttribute("src", baseUri + stream.getAtomEntitySource());
	        writer.endElement("content");
	      }
	    }

	    if (!hasStream) {
	      writer.startElement("content");
	      writer.writeAttribute("type", MediaType.APPLICATION_XML);
	    }

	    writer.startElement(new QName2(m, "properties", "m"));
	    writeProperties(writer, entityProperties);
	    writer.endElement("properties");

	    if (!hasStream) {
	      writer.endElement("content");
	    }
	    return absid;
  }  

  /*
   * helper function to write customized and generic link
   */
  private void writeLink(XMLWriter2 writer, OLink olink, String type, String href, Collection<Link> linkId, Collection<Link> linkIdUsed) {
	// deferred link.
	  String rel = olink.getRelation();
	  String title = olink.getTitle();
	  String id = "";
	  if(linkId != null ) {
		  Iterator<Link> iterator = linkId.iterator();
		  while(iterator.hasNext()) {
			  Link tempLink = iterator.next();
			  if(tempLink.getHref().endsWith(href) && !linkIdUsed.contains(tempLink)) {
				  id = tempLink.getLinkId();
				  linkIdUsed.add(tempLink);
				  break;
			  }
		  }
	  }
      if (!"self".equals(rel) && !"edit".equals(rel)) {
    	  if(id != null && id.length() > 0 ) {
    		  writeElement(writer, "link", null, "rel", rel, "type", type, "title", title, "href", href, "id", id);
    	  } else {
    		  writeElement(writer, "link", null, "rel", rel, "type", type, "title", title, "href", href);
    	  }
      } else {
    	  if(id != null && id.length() > 0 ) {
    		  writeElement(writer, "link", null, "rel", rel, "title", title, "href", href, "id", id);
    	  } else {
    	      
    	      List<StringBuilder> profileAndHref = createProfileForStateName(href);
    	      
    	      if("self".equals(rel) && null!=profileAndHref) {
    	          writeElement(writer, "link", null, "rel", rel, "profile" , profileAndHref.get(0).toString() , "title", title, "href", profileAndHref.get(1).toString()); 
    	      } else {
    	          writeElement(writer, "link", null, "rel", rel, "title", title, "href", href);
    	      }
    	      
    	      profileAndHref = null;
    	  }
      }
  }
  
  private OAtomEntity getAtomInfo(OEntity oe) {
	    if (oe != null) {
	      OAtomEntity atomEntity = oe.findExtension(OAtomEntity.class);
	      if (atomEntity != null)
	        return atomEntity;
	    }
	    return new OAtomEntity() {
	      @Override
	      public String getAtomEntityTitle() {
	        return null;
	      }

	      @Override
	      public String getAtomEntitySummary() {
	        return null;
	      }

	      @Override
	      public String getAtomEntityAuthor() {
	        return null;
	      }

	      @Override
	      public LocalDateTime getAtomEntityUpdated() {
	        return null;
	      }
	    };
	  }
  
  @Override
  // the original implementation that uses the OEntity EdmEntitySet
  public void write(UriInfo uriInfo, Writer w, EntityResponse target) {
	    EdmEntitySet ees = target.getEntity().getEntitySet();
	    write(uriInfo, w, target, ees, target.getEntity().getLinks());
  }
  
    private List<StringBuilder> createProfileForStateName(String href) {
        
        if (null!=href && href.contains("#@")) {
            List<StringBuilder> list = new ArrayList<StringBuilder>();
            String [] hrefSplit = href.split("#@");
            
            StringBuilder rel = new StringBuilder("http://schemas.microsoft.com/ado/2007/08/dataservices/related/");
            rel.append(hrefSplit[1]);          
            
            StringBuilder cleanHref = new StringBuilder(hrefSplit[0]);
            list.add(0, rel);
            list.add(1, cleanHref);
            
            return list;
        } else {
            return null;
        }
    }
}
