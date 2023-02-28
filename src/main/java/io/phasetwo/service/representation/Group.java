package io.phasetwo.service.representation;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Group {

    private @Valid String id = null;

    @NotNull
    @NotBlank
    private String name = null;
    private @Valid String description = null;

    private @Valid String parentId;
    private @Valid Map<String, List<String>> attributes = new HashMap<String, List<String>>();

    public Group id(String id) {
        this.id = id;
        return this;
    }

    @JsonProperty("id")
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Group name(String name) {
        this.name = name;
        return this;
    }

    @JsonProperty("name")
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @JsonProperty("description")
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Group description(String description) {
        this.description = description;
        return this;
    }

    public Group parentId(String parentId) {
        this.parentId = parentId;
        return this;
    }

    @JsonProperty("parentId")
    public String getParentId() {
        return parentId;
    }

    @JsonProperty("parentId")
    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    public Group attributes(Map<String, List<String>> attributes) {
        this.attributes = attributes;
        return this;
    }

    @JsonProperty("attributes")
    public Map<String, List<String>> getAttributes() {
        return attributes;
    }

    @JsonProperty("attributes")
    public void setAttributes(Map<String, List<String>> attributes) {
        this.attributes = attributes;
    }

}
