package org.openmrs.module.sync2.client.rest.impl;

import org.openmrs.module.sync2.SyncCategoryConstants;
import org.openmrs.module.sync2.client.rest.RestResourceConverter;
import org.openmrs.module.webservices.rest.SimpleObject;
import org.springframework.stereotype.Component;

import java.util.AbstractMap;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.openmrs.module.sync2.SyncCategoryConstants.CATEGORY_FORM;
import static org.openmrs.module.sync2.SyncCategoryConstants.CATEGORY_OBSERVATION;
import static org.openmrs.module.sync2.SyncCategoryConstants.CATEGORY_PATIENT;
import static org.openmrs.module.sync2.SyncCategoryConstants.CATEGORY_PERSON;
import static org.openmrs.module.sync2.SyncCategoryConstants.CATEGORY_VISIT;

@Component("sync2.RestResourceConverter")
public class RestResourceConverterImpl implements RestResourceConverter {

	private static final String URL_DELIMITER = "/";

	private static final List<String> STOP_WORDS = Arrays.asList("links", "auditInfo");

	@Override
	public void convertObject(String url, Object object) {
		if (object instanceof SimpleObject) {
			SimpleObject simpleObject = (SimpleObject) object;
			String category = getCategoryFromUrl(url);
			switch (category) {
				case CATEGORY_PATIENT:
					convertPatient(simpleObject);
					break;
				case CATEGORY_OBSERVATION:
					convertObservation(simpleObject);
					break;
				case CATEGORY_VISIT:
					convertVisit(simpleObject);
					break;
				case CATEGORY_FORM:
					convertForm(simpleObject);
					break;
				case CATEGORY_PERSON:
					convertPersonResource(simpleObject);
					break;
			}
		}
	}

	@Override
	public boolean deepCompareSimpleObject(AbstractMap<String, Object> from, AbstractMap<String, Object> dest) {
		boolean equals = true;

		for (Map.Entry<String, Object> entry : from.entrySet()){
			String key = entry.getKey();
			Object value = entry.getValue();
			if (STOP_WORDS.contains(key)) {
				continue;
			}

			if (!dest.containsKey(key)) {
				equals = false;
			} else {
				Object destValue = dest.get(key);
				if (value == null && destValue == null) {
					continue;
				} else if (value instanceof Map<?,?>) {
					equals = deepCompareSimpleObject((AbstractMap) value, (AbstractMap) destValue);
				} else if (value instanceof List<?>) {
					deepCompareList((List<Object>) value, (List<Object>) destValue);
				} else if (destValue == null || !destValue.equals(value)) {
					equals = false;
				}
			}

			if (!equals) {
				break;
			}
		}

		return equals;
	}

	public boolean deepCompareList(List<Object> from, List<Object> dest) {
		boolean equals = true;

		if (from.size() != dest.size()) {
			equals = false;
		} else {
			for (int i = 0; i < from.size(); i++) {
				Object element = from.get(i);
				if (element instanceof String) {
					equals = dest.contains(element);
				} else if (element instanceof Map<?,?>) {
					deepCompareSimpleObject((AbstractMap<String,Object>) element,
							(AbstractMap<String, Object>) dest.get(i));
				} else {
					equals = element.equals(dest.get(i));
				}
			}
		}

		return equals;
	}

	private String getCategoryFromUrl(String url) {
		String category = "";
		if (url.contains(URL_DELIMITER)) {
			String[] tokens = url.split(URL_DELIMITER);
            category = tokens[tokens.length - 1];
            //The rest url for observations is /ws/rest/v1/obs therefore the value of category from
            //the line above would be obs but we don't want that since this module uses
            //'observation' for the category
            if (category.equals("obs")) {
                category = SyncCategoryConstants.CATEGORY_OBSERVATION;
            }
		}
		return category;
	}

	private void convertPatient(Map<String, Object> simpleObject) {
		convertPersonResource((Map<String, Object>) simpleObject.get("person"));
	}

	private void convertPersonResource(Map<String, Object> simpleObject) {
		simpleObject.remove("preferredName");
		simpleObject.remove("preferredAddress");
	}

	private void convertObservation(Map<String, Object> simpleObject) {
		Map concept = (Map<String, Object>) simpleObject.get("concept");
		simpleObject.remove("concept");
		simpleObject.put("concept", concept.get("uuid"));
	}

	private void convertVisit(Map<String, Object> simpleObject) {
		simpleObject.remove("preferredName");
	}

	private void convertForm(SimpleObject simpleObject) {
		simpleObject.remove("formFields");
		simpleObject.remove("resources");
	}
}
