package fr.anisekai.proxy;

import java.util.List;
import java.util.Map;

public class ExampleEntity {

    public static ExampleEntity create() {

        return create(1L);
    }

    public static ExampleEntity create(long id) {

        ExampleEntity entity = new ExampleEntity();
        entity.setId(id);
        entity.setActive(true);
        entity.setTags(null);
        entity.setMapping(null);
        entity.setIgnored(true);
        return entity;
    }


    private Long                       id;
    private String                     name;
    private boolean                    active;
    private List<String>               tags;
    private Map<String, String>        mapping;
    private boolean                    ignoreThis;
    private ExampleEntity              entity;
    private List<ExampleEntity>        entities;
    private Map<String, ExampleEntity> entityMap;

    public Long getId() {

        return this.id;
    }

    public void setId(Long id) {

        this.id = id;
    }

    public String getName() {

        return this.name;
    }

    public void setName(String name) {

        this.name = name;
    }

    public boolean isActive() {

        return this.active;
    }

    public void setActive(boolean active) {

        this.active = active;
    }

    public List<String> getTags() {

        return this.tags;
    }

    public void setTags(List<String> tags) {

        this.tags = tags;
    }

    public Map<String, String> getMapping() {

        return this.mapping;
    }

    public void setMapping(Map<String, String> mapping) {

        this.mapping = mapping;
    }

    public boolean isIgnored() {

        return this.ignoreThis;
    }

    public void setIgnored(boolean ignoreThis) {

        this.ignoreThis = ignoreThis;
    }

    public void setIgnoreThis(boolean ignoreThis) {

        this.ignoreThis = ignoreThis;
    }

    public ExampleEntity getEntity() {

        return this.entity;
    }

    public void setEntity(ExampleEntity entity) {

        this.entity = entity;
    }

    public List<ExampleEntity> getEntities() {

        return this.entities;
    }

    public void setEntities(List<ExampleEntity> entities) {

        this.entities = entities;
    }

    public Map<String, ExampleEntity> getEntityMap() {

        return this.entityMap;
    }

    public void setEntityMap(Map<String, ExampleEntity> entityMap) {

        this.entityMap = entityMap;
    }

    @Override
    public String toString() {

        return "ExampleEntity{id=%d, name='%s', active=%s, tags=%s, mapping=%s, ignoreThis=%s, entity=%s}".formatted(
                this.id,
                this.name,
                this.active,
                this.tags,
                this.mapping,
                this.ignoreThis,
                this.entity
        );
    }

}
