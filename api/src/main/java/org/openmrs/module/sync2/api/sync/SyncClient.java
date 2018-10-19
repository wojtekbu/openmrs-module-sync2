package org.openmrs.module.sync2.api.sync;

import org.openmrs.api.AdministrationService;
import org.openmrs.api.context.Context;
import org.openmrs.module.fhir.api.client.FHIRHttpMessageConverter;
import org.openmrs.module.fhir.api.client.HeaderClientHttpRequestInterceptor;
import org.openmrs.module.fhir.api.helper.ClientHelper;
import org.openmrs.module.sync2.api.exceptions.SyncException;
import org.openmrs.module.sync2.api.model.RequestWrapper;
import org.openmrs.module.sync2.api.model.enums.OpenMRSSyncInstance;
import org.openmrs.module.sync2.client.ClientHelperFactory;
import org.openmrs.module.sync2.client.RequestWrapperConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import static org.openmrs.module.sync2.SyncConstants.ACTION_CREATED;
import static org.openmrs.module.sync2.SyncConstants.ACTION_UPDATED;
import static org.openmrs.module.sync2.SyncConstants.ACTION_VOIDED;
import static org.openmrs.module.sync2.SyncConstants.LOCAL_PASSWORD_PROPERTY;
import static org.openmrs.module.sync2.SyncConstants.LOCAL_USERNAME_PROPERTY;
import static org.openmrs.module.sync2.SyncConstants.PARENT_PASSWORD_PROPERTY;
import static org.openmrs.module.sync2.SyncConstants.PARENT_USERNAME_PROPERTY;
import static org.openmrs.module.sync2.SyncConstants.SYNC2_REST_ENDPOINT;
import static org.openmrs.module.sync2.api.model.enums.OpenMRSSyncInstance.PARENT;
import static org.openmrs.module.sync2.api.utils.SyncUtils.getLocalBaseUrl;
import static org.openmrs.module.sync2.api.utils.SyncUtils.getParentBaseUrl;
import static org.openmrs.module.sync2.api.utils.SyncUtils.getSyncConfigurationService;

public class SyncClient {

	private static final Logger LOGGER = LoggerFactory.getLogger(SyncClient.class);

	private String username;

	private String password;

	private RestTemplate restTemplate = new RestTemplate();

	public Object pullData(String category, String clientName, String resourceUrl, OpenMRSSyncInstance instance) {
		Object result = null;
		setUpCredentials(instance);

		ClientHelper clientHelper = new ClientHelperFactory().createClient(clientName);
		prepareRestTemplate(clientHelper);
		String destinationUrl = getDestinationUri(instance);

		try {
			result = retrieveObject(category, resourceUrl, destinationUrl, clientName);
		}
		catch (HttpClientErrorException e) {
			if (!e.getStatusCode().equals(HttpStatus.NOT_FOUND)) {
				throw new SyncException("Error during reading local object: ", e);
			}
		}
		catch (URISyntaxException e) {
			LOGGER.error(e.getMessage());
		}
		return result;
	}

	public ResponseEntity<String> pushData(String category, Object object, String clientName,
			String resourceUrl, String action, OpenMRSSyncInstance instance) {
		ResponseEntity<String> result = null;
		setUpCredentials(instance);
		String destinationUrl = getDestinationUri(instance);
		ClientHelper clientHelper = new ClientHelperFactory().createClient(clientName);
		prepareRestTemplate(clientHelper);

		try {
			switch (action) {
				case ACTION_CREATED:
					result = createObject(category, resourceUrl, destinationUrl, object, clientName);
					break;
				case ACTION_UPDATED:
					result = updateObject(category, resourceUrl, destinationUrl, object, clientName);
					break;
				case ACTION_VOIDED:
					result = deleteObject(category, resourceUrl, destinationUrl, (String) object, clientName);
					break;
				default:
					LOGGER.warn(String.format("Sync push exception. Unrecognized action: %s", action));
					break;
			}
		} catch (HttpClientErrorException | HttpServerErrorException e) {
			throw new SyncException(String.format("Object posting error. Code: %d. Details: \n%s",
					e.getStatusCode().value(), e.getResponseBodyAsString()), e);
		} catch (URISyntaxException e) {
			LOGGER.error(e.getMessage());
		}
		return result;
	}

	private void setUpCredentials(OpenMRSSyncInstance instance) {
		AdministrationService adminService = Context.getAdministrationService();

		this.username = instance.equals(PARENT) ? adminService.getGlobalProperty(PARENT_USERNAME_PROPERTY) :
				adminService.getGlobalProperty(LOCAL_USERNAME_PROPERTY);

		this.password = instance.equals(PARENT) ? adminService.getGlobalProperty(PARENT_PASSWORD_PROPERTY) :
				adminService.getGlobalProperty(LOCAL_PASSWORD_PROPERTY);
	}

	private void prepareRestTemplate(ClientHelper clientHelper) {
		restTemplate.setInterceptors(clientHelper.getCustomInterceptors(this.username, this.password));
		restTemplate.setMessageConverters(clientHelper.getCustomFHIRMessageConverter());

		List<HttpMessageConverter<?>> converters = new ArrayList<>(clientHelper.getCustomFHIRMessageConverter());
		converters.add(new RequestWrapperConverter());
		restTemplate.setMessageConverters(converters);
	}

	private Object retrieveObject(String category, String resourceUrl, String destinationUrl, String clientName)
			throws RestClientException, URISyntaxException {
		ClientHelper clientHelper = new ClientHelperFactory().createClient(clientName);
		Class<?> clazz = clientHelper.resolveCategoryByCategory(category);
		RequestWrapper requestWrapper = createWrappedRequest(clientHelper.retrieveRequest(resourceUrl), clientName, clazz.getCanonicalName());
		RequestEntity<RequestWrapper> request = new RequestEntity<>(requestWrapper, HttpMethod.POST,  new URI(destinationUrl + "/get"));
		return restTemplate.exchange(request, clazz).getBody();
	}

	private ResponseEntity<String> createObject(String category, String resourceUrl, String destinationUrl, Object object, String clientName)
			throws RestClientException, URISyntaxException {
		ClientHelper clientHelper = new ClientHelperFactory().createClient(clientName);
		Class<?> clazz = clientHelper.resolveCategoryByCategory(category);

		RequestWrapper requestWrapper = createWrappedRequest(clientHelper.createRequest(resourceUrl, object), clientName, clazz.getCanonicalName());
		RequestEntity<RequestWrapper> request = new RequestEntity<>(requestWrapper, HttpMethod.POST, new URI(destinationUrl + "/post"));
		return restTemplate.exchange(request, String.class);
	}

	private ResponseEntity<String> deleteObject(String category, String resourceUrl, String destinationUrl, String uuid, String clientName)
			throws URISyntaxException {
		ClientHelper clientHelper = new ClientHelperFactory().createClient(clientName);
		Class<?> clazz = clientHelper.resolveCategoryByCategory(category);
		RequestWrapper requestWrapper = createWrappedRequest(clientHelper.deleteRequest(resourceUrl, uuid), clientName, clazz.getCanonicalName());
		RequestEntity<RequestWrapper> request = new RequestEntity<>(requestWrapper, HttpMethod.POST,  new URI(destinationUrl + "/delete"));
		return restTemplate.exchange(request, String.class);
	}

	private ResponseEntity<String> updateObject(String category, String resourceUrl, String destinationUrl, Object object, String clientName)
			throws URISyntaxException {
		ClientHelper clientHelper = new ClientHelperFactory().createClient(clientName);
		Class<?> clazz = clientHelper.resolveCategoryByCategory(category);
		RequestWrapper requestWrapper = createWrappedRequest(clientHelper.updateRequest(resourceUrl, object), clientName, clazz.getCanonicalName());
		RequestEntity<RequestWrapper> request = new RequestEntity<>(requestWrapper, HttpMethod.POST, new URI(destinationUrl + "/put"));
		return restTemplate.exchange(request, String.class);
	}

	private String getDestinationUri(OpenMRSSyncInstance instance) {
		String uri = "";
		switch (instance) {
			case PARENT:
				uri = getParentBaseUrl();
				break;
			case CHILD:
				uri = getLocalBaseUrl();
				break;
		}
		return uri + SYNC2_REST_ENDPOINT;
	}

	private RequestWrapper createWrappedRequest(RequestEntity requestEntity, String clientName, String className) {
		String instanceId = getSyncConfigurationService().getSyncConfiguration().getGeneral().getLocalInstanceId();
		RequestWrapper requestWrapper = new RequestWrapper();
		requestWrapper.setInstanceId(instanceId);
		requestWrapper.setRequestEntity(requestEntity);
		requestWrapper.setClientName(clientName);
		requestWrapper.setClassName(className);
		return requestWrapper;
	}

	private void setup(String client) {
		ClientHelper restHelper = new ClientHelperFactory().createClient(client);

		List<HttpMessageConverter<?>> converters = new ArrayList<>(restHelper.getCustomFHIRMessageConverter());
		converters.add(new RequestWrapperConverter());
		restTemplate.setMessageConverters(converters);

		restTemplate.setInterceptors(restHelper.getCustomInterceptors(this.username, this.password));
	}
}
