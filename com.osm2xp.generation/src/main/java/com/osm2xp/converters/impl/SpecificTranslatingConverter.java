package com.osm2xp.converters.impl;

import java.awt.Color;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.osm2xp.core.exceptions.Osm2xpBusinessException;
import com.osm2xp.core.model.osm.Tag;
import com.osm2xp.core.model.osm.Way;
import com.osm2xp.datastore.IDataSink;
import com.osm2xp.translators.ISpecificTranslator;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Polygon;

public class SpecificTranslatingConverter extends AbstractTranslatingConverter {

	public SpecificTranslatingConverter(ISpecificTranslator translator, IDataSink processor,
			Map<Long, Color> roofsColorMap) {
		super(translator, processor, roofsColorMap);
	}

	@Override
	protected void translateWay(Way way, List<Long> ids) throws Osm2xpBusinessException {
		Geometry geometry = getGeometry(ids);
		if (geometry != null) {
			List<Geometry> geoms = fix(Collections.singletonList(geometry));
			((ISpecificTranslator) translator).processWays(way.getId(), way.getTags(), geometry, geoms);
		}
	}

	@Override
	protected void translatePolys(long id, List<Tag> tagsModel, List<Polygon> cleanedPolys)
			throws Osm2xpBusinessException {
		((ISpecificTranslator) translator).processWays(id, tagsModel, null, cleanedPolys);
	}

}
