package com.osm2xp.model.osm;

import java.util.List;

public interface IHasTags {

	String getTagValue(String tagKey);

	List<Tag> getTags();

}
