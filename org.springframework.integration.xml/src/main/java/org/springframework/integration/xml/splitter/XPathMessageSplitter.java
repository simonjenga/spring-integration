/*
 * Copyright 2002-2007 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.integration.xml.splitter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;

import org.springframework.integration.message.Message;
import org.springframework.integration.message.MessagingException;
import org.springframework.integration.splitter.AbstractSplitter;
import org.springframework.integration.xml.DefaultXmlPayloadConverter;
import org.springframework.integration.xml.XmlPayloadConverter;
import org.springframework.util.Assert;
import org.springframework.xml.transform.StringResult;
import org.springframework.xml.xpath.XPathExpression;
import org.springframework.xml.xpath.XPathExpressionFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Use {@link XPathExpression} to split a {@link Document} or {@link String}
 * payload into a {@link NodeList} each {@link Node} is then
 * @author Jonas Partner
 * 
 */
public class XPathMessageSplitter extends AbstractSplitter {

	private final XPathExpression xpathExpression;

	private volatile boolean createDocuments;

	private volatile DocumentBuilderFactory documentBuilderFactory;

	private volatile XmlPayloadConverter xmlPayloadConverter = new DefaultXmlPayloadConverter();

	public XPathMessageSplitter(String expression) {
		this(expression, new HashMap<String, String>());
	}

	public XPathMessageSplitter(String expression, Map<String, String> namespaces) {
		this(XPathExpressionFactory.createXPathExpression(expression, namespaces));
	}

	public XPathMessageSplitter(XPathExpression xpathExpression) {
		this.xpathExpression = xpathExpression;
		this.documentBuilderFactory = DocumentBuilderFactory.newInstance();
		this.documentBuilderFactory.setNamespaceAware(true);
	}

	@Override
	protected Object splitMessage(Message<?> message) {
		try {
			Object payload = message.getPayload();
			Object toReturn = null;

			if (payload instanceof Node) {
				toReturn = splitNodePayload((Node) payload, message);
			}
			else if (payload instanceof String) {
				payload = xmlPayloadConverter.convertToDocument(payload);
				toReturn = splitStringPayload(message);
			}
			return toReturn;
		}
		catch (ParserConfigurationException e) {
			throw new MessagingException(message, "Error creating DocumentBuilder", e);
		}
		catch (Exception e) {
			throw new MessagingException(message, "Error transforming payload", e);
		}

	}

	private Object splitStringPayload(Message<?> message) throws ParserConfigurationException,
			TransformerFactoryConfigurationError, TransformerException {
		Node node = xmlPayloadConverter.convertToDocument(message.getPayload());
		List<Node> nodes = splitNodePayload(node, message);
		Transformer transformer = TransformerFactory.newInstance().newTransformer();
		List<String> splitStrings = new ArrayList<String>(nodes.size());
		for (Node nodeFromList : nodes) {
			StringResult result = new StringResult();
			transformer.transform(new DOMSource(nodeFromList), result);
			splitStrings.add(result.toString());
		}
		return splitStrings;
	}

	@SuppressWarnings("unchecked")
	protected List<Node> splitNodePayload(Node node, Message message) throws ParserConfigurationException {
		List<Node> nodeList = xpathExpression.evaluateAsNodeList(node);
		if (nodeList.size() == 0) {
			throw new MessagingException(message, "Could not split message with XPath " + xpathExpression);
		}
		if (createDocuments) {
			return convertNodesToDocuments(nodeList);
		}
		return nodeList;

	}

	private List<Node> convertNodesToDocuments(List<Node> nodeList) throws ParserConfigurationException {
		DocumentBuilder documentBuilder;
		documentBuilder = getNewDocumentBuilder();

		List<Node> docList = new ArrayList<Node>(nodeList.size());
		for (Node node : nodeList) {
			Document doc = documentBuilder.newDocument();
			doc.importNode(node, true);
			docList.add(doc);
		}
		return docList;
	}

	public void setCreateDocuments(boolean createDocuments) {
		this.createDocuments = createDocuments;
	}

	public void setDocumentBuilder(DocumentBuilderFactory documentBuilderFactory) {
		Assert.notNull(documentBuilderFactory, "Document builder can not be null");
		this.documentBuilderFactory = documentBuilderFactory;
	}

	public void setXmlPayloadConverter(XmlPayloadConverter xmlPayloadConverter) {
		Assert.notNull(xmlPayloadConverter, "Xml Payload converter can not be null");
		this.xmlPayloadConverter = xmlPayloadConverter;

	}

	protected DocumentBuilder getNewDocumentBuilder() throws ParserConfigurationException {
		synchronized (documentBuilderFactory) {
			return documentBuilderFactory.newDocumentBuilder();
		}
	}

}
