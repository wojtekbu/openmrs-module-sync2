package org.openmrs.module.sync2.api.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.TypeAdapter;
import org.openmrs.module.sync2.api.model.audit.AuditMessage;
import org.openmrs.module.sync2.api.utils.ContextUtils;
import org.springframework.core.convert.ConversionService;
import org.springframework.http.RequestEntity;

import java.io.Serializable;
import java.lang.reflect.Type;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Objects;

public class RequestWrapper implements Serializable {

    private String instanceId;
    private RequestEntity requestEntity;
    private String clientName;
    private String className;

    public RequestWrapper() {
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public RequestEntity getRequestEntity() {
        return requestEntity;
    }

    public void setRequestEntity(RequestEntity requestEntity) {
        this.requestEntity = requestEntity;
    }

    public String getClientName() {
        return clientName;
    }

    public void setClientName(String clientName) {
        this.clientName = clientName;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RequestWrapper)) return false;
        RequestWrapper that = (RequestWrapper) o;
        return Objects.equals(instanceId, that.instanceId) &&
                Objects.equals(requestEntity, that.requestEntity) &&
                Objects.equals(clientName, that.clientName) &&
                Objects.equals(className, that.className);
    }

    @Override
    public int hashCode() {
        return Objects.hash(instanceId, requestEntity, clientName, className);
    }

    public static class RequestWrapperSerializer implements JsonSerializer<RequestWrapper> {
        private static final String ISO_8601 = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";
        @Override
        public JsonElement serialize(RequestWrapper src, Type typeOfSrc, JsonSerializationContext context) {
                Gson gson = createDefaultGson();
                return gson.toJsonTree(src);

        }
        private Gson createDefaultGson() {
            // Trick to get the DefaultDateTypeAdatpter instance
            // Create a first Gson instance
            Gson gson = new GsonBuilder()
                    .setDateFormat(ISO_8601)
                    .create();

            // Get the date adapter
            TypeAdapter<Date> dateTypeAdapter = gson.getAdapter(Date.class);

            // Ensure the DateTypeAdapter is null safe
            TypeAdapter<Date> safeDateTypeAdapter = dateTypeAdapter.nullSafe();

            // Build the definitive safe Gson instance
            return new GsonBuilder()
                    .setDateFormat(ISO_8601)
                    .registerTypeAdapter(Date.class, safeDateTypeAdapter)
                    .create();
        }
    }

    public static class RequestWrapperDeserializer implements JsonDeserializer<RequestWrapper> {
        private static final String ISO_8601 = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";
        @Override
        public RequestWrapper deserialize(JsonElement json, Type type,
                                        JsonDeserializationContext jsonDeserializationContext) {
                Gson gson = createDefaultGson();
                return gson.fromJson(json, RequestWrapper.class);
        }
        private Gson createDefaultGson() {
            // Trick to get the DefaultDateTypeAdatpter instance
            // Create a first Gson instance
            Gson gson = new GsonBuilder()
                    .setDateFormat(ISO_8601)
                    .create();

            // Get the date adapter
            TypeAdapter<Date> dateTypeAdapter = gson.getAdapter(Date.class);

            // Ensure the DateTypeAdapter is null safe
            TypeAdapter<Date> safeDateTypeAdapter = dateTypeAdapter.nullSafe();

            // Build the definitive safe Gson instance
            return new GsonBuilder()
                    .setDateFormat(ISO_8601)
                    .registerTypeAdapter(Date.class, safeDateTypeAdapter)
                    .create();
        }
    }
}
