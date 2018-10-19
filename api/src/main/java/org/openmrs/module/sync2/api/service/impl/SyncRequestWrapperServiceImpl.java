package org.openmrs.module.sync2.api.service.impl;

import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.map.ObjectMapper;
import org.openmrs.User;
import org.openmrs.api.AdministrationService;
import org.openmrs.api.context.Context;
import org.openmrs.module.fhir.api.helper.ClientHelper;
import org.openmrs.module.sync2.api.exceptions.SyncException;
import org.openmrs.module.sync2.api.model.RequestWrapper;
import org.openmrs.module.sync2.api.model.audit.AuditMessage;
import org.openmrs.module.sync2.api.service.SyncConfigurationService;
import org.openmrs.module.sync2.api.service.SyncRequestWrapperService;
import org.openmrs.module.sync2.client.ClientHelperFactory;
import org.openmrs.module.sync2.client.RequestWrapperConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.openmrs.module.sync2.SyncConstants.LOCAL_PASSWORD_PROPERTY;
import static org.openmrs.module.sync2.SyncConstants.LOCAL_USERNAME_PROPERTY;

@Component("sync2.syncRequestWrapperService")
public class SyncRequestWrapperServiceImpl implements SyncRequestWrapperService {

    private final static ResponseEntity<String> NOT_FOUND_RESPONSE = new ResponseEntity<>(
            "The entity doesn't exists", HttpStatus.NOT_FOUND);

    @Autowired
    private SyncConfigurationService configuration;

    private RestTemplate restTemplate = new RestTemplate();

    @Override
    public ResponseEntity<String> sendPostRequest(RequestWrapper wrapper) {
        prepareRestTemplate(wrapper.getClientName());
        RequestEntity request = wrapper.getRequestEntity();
        try {
            return restTemplate.exchange(convertBody(wrapper), String.class);
        }
        catch (HttpClientErrorException e) {
            return new ResponseEntity<>(e.getMessage(), e.getStatusCode());
        }
        catch (ClassNotFoundException | IOException e) {
            // TODO
            return NOT_FOUND_RESPONSE;
        }
    }

    @Override
    public ResponseEntity<String> sendPutRequest(RequestWrapper wrapper) {
        prepareRestTemplate(wrapper.getClientName());
        RequestEntity request = wrapper.getRequestEntity();
        try {
            return restTemplate.exchange(request, String.class);
        }
        catch (HttpClientErrorException e) {
            return new ResponseEntity<>(e.getMessage(), e.getStatusCode());
        }
    }

    @Override
    public ResponseEntity<String> sendDeleteRequest(RequestWrapper wrapper) {
        prepareRestTemplate(wrapper.getClientName());
        RequestEntity request = wrapper.getRequestEntity();
        try {
            return restTemplate.exchange(request, String.class);
        }
        catch (HttpClientErrorException e) {
            return new ResponseEntity<>(e.getMessage(), e.getStatusCode());
        }
    }

    @Override
    public ResponseEntity<String> sendGetRequest(RequestWrapper wrapper) {
        prepareRestTemplate(wrapper.getClientName());
        RequestEntity request = wrapper.getRequestEntity();
        try {
            return restTemplate.exchange(request, String.class);
        }
        catch (HttpClientErrorException e) {
           return new ResponseEntity<>(e.getMessage(), e.getStatusCode());
        }
    }

    @Override
    public boolean isRequestAuthenticated(RequestWrapper requestWrapper) {
        User user = Context.getAuthenticatedUser();
        if (user == null) return false;

        return isInstanceIdValid(requestWrapper.getInstanceId());
    }

    private boolean isInstanceIdValid(String instanceId) {
        return isWhitelistDisabled() || isInstanceIdOnWhitelist(instanceId);
    }

    private boolean isInstanceIdOnWhitelist(String instanceId) {
        return configuration.getSyncConfiguration().getWhitelist().getInstanceIds().contains(instanceId) &&
                configuration.getSyncConfiguration().getWhitelist().isEnabled();
    }

    private boolean isWhitelistDisabled() {
        return !configuration.getSyncConfiguration().getWhitelist().isEnabled();
    }

    private void prepareRestTemplate(String client) {
        AdministrationService adminService = Context.getAdministrationService();
        String username = adminService.getGlobalProperty(LOCAL_USERNAME_PROPERTY);
        String password = adminService.getGlobalProperty(LOCAL_PASSWORD_PROPERTY);

        ClientHelper helper = new ClientHelperFactory().createClient(client);

        restTemplate.setMessageConverters(helper.getCustomFHIRMessageConverter());
        restTemplate.setInterceptors(helper.getCustomInterceptors(username, password));
    }

    private RequestEntity convertBody(RequestWrapper wrapper) throws IOException, ClassNotFoundException{
        RequestEntity innerRequest = wrapper.getRequestEntity();
        Object body = innerRequest.getBody();
        String stringBody = new ObjectMapper().writeValueAsString(body);
        Class<?> clazz = Class.forName(wrapper.getClassName());
        RequestEntity request =  new RequestEntity(new ObjectMapper().readValue(stringBody, clazz), innerRequest.getMethod(), innerRequest.getUrl());
        return request;
    }
}
