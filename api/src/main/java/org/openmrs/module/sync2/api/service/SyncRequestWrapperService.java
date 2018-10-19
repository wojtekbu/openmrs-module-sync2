package org.openmrs.module.sync2.api.service;

import org.openmrs.module.sync2.api.model.RequestWrapper;
import org.springframework.http.ResponseEntity;

public interface SyncRequestWrapperService {

    ResponseEntity<String> sendPostRequest(RequestWrapper requestWrapper);

    ResponseEntity<String> sendPutRequest(RequestWrapper requestWrapper);


    ResponseEntity<String> sendDeleteRequest(RequestWrapper requestWrapper);

    ResponseEntity<String> sendGetRequest(RequestWrapper requestWrapper);

    boolean isRequestAuthenticated(RequestWrapper requestWrapper);
}
