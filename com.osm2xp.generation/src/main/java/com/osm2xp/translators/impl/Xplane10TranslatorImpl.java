package com.osm2xp.translators.impl;

import java.util.Random;

import com.osm2xp.model.osm.polygon.OsmPolygon;
import com.osm2xp.model.osm.polygon.OsmPolyline;
import com.osm2xp.utils.DsfObjectsProvider;
import com.osm2xp.utils.geometry.GeomUtils;
import com.osm2xp.generation.options.GlobalOptionsProvider;
import com.osm2xp.generation.options.XPlaneOptionsProvider;
import com.osm2xp.utils.osm.OsmUtils;
import com.osm2xp.utils.xplane.XplaneExclusionsHelper;
import com.osm2xp.writers.IHeaderedWriter;

import math.geom2d.Point2D;
import math.geom2d.polygon.LinearRing2D;

/**
 * Xplane 10 translator implementation. Generates XPlane scenery from osm data.
 * 
 * @author Benjamin Blanchet
 * 
 */
public class Xplane10TranslatorImpl extends XPlaneTranslatorImpl {

	/**
	 * Smart exclusions helper.
	 */
	XplaneExclusionsHelper exclusionsHelper = new XplaneExclusionsHelper();
	
	/**
	 * Constructor.
	 * 
	 * @param stats
	 *            stats object.
	 * @param writer
	 *            file writer.
	 * @param currentTile
	 *            current lat/lon tile.
	 * @param folderPath
	 *            generated scenery folder path.
	 * @param dsfObjectsProvider
	 *            dsf object provider.
	 */
	public Xplane10TranslatorImpl(IHeaderedWriter writer,
			Point2D currentTile, String folderPath,
			DsfObjectsProvider dsfObjectsProvider) {
		super(writer, currentTile, folderPath, dsfObjectsProvider);
	}

	@Override
	protected String getExclusionsStr() {
		if (XPlaneOptionsProvider.getOptions().isSmartExclusions()) {
			return exclusionsHelper.exportExclusions();
		}
		return super.getExclusionsStr();
	}

	@Override
	public void init() {
		super.init();
		// exclusionHelper
		if (XPlaneOptionsProvider.getOptions().isSmartExclusions()) {
			exclusionsHelper.run();
		}
	}

	/**
	 * Write streetlight objects in dsf file.
	 * 
	 * @param osmPolygon
	 *            osm road polygon
	 */
	public void writeStreetLightToDsf(OsmPolygon osmPolygon) {
		// init d'un entier pour modulo densit� street lights
		Integer densityIndex = 0;
		if (XPlaneOptionsProvider.getOptions().getLightsDensity() == 0) {
			densityIndex = 10;
		} else {
			if (XPlaneOptionsProvider.getOptions().getLightsDensity() == 1) {
				densityIndex = 5;
			} else {
				if (XPlaneOptionsProvider.getOptions().getLightsDensity() == 2)
					densityIndex = 3;
			}
		}
		StringBuffer sb = new StringBuffer();
		LinearRing2D polygon = osmPolygon.getPolygon();
		for (int i = 0; i < polygon.vertices().size(); i++) {
			if ((i % densityIndex) == 0) {
				Point2D lightLoc = polygon.vertex(i);
				polygon.setVertex(i, lightLoc.translate(0.0001, 0.0001));
				if (GeomUtils.compareCoordinates(lightLoc, currentTile)) {
					Random randomGenerator = new Random();
					int orientation = randomGenerator.nextInt(360);
					sb.append("OBJECT "
							+ dsfObjectsProvider.getRandomStreetLightObject()
							+ " " + (lightLoc.y()) + " " + (lightLoc.x()) + " "
							+ orientation);
					sb.append(LINE_SEP);
				}
			}
		}

		writer.write(sb.toString());
	}

	
	/**
	 * Construct and write a facade building in the dsf file.
	 * 
	 * @param osmPolygon
	 *            osm polygon
	 * @return true if a building has been gennerated in the dsf file.
	 */
	protected boolean processBuilding(OsmPolyline polyline) {
		if (!(polyline instanceof OsmPolygon)) {
			return false;
		}
		OsmPolygon osmPolygon = (OsmPolygon) polyline;
		boolean result = false;
		if (XPlaneOptionsProvider.getOptions().isGenerateBuildings()
				&& OsmUtils.isBuilding(osmPolygon.getTags())
				&& !OsmUtils.isExcluded(osmPolygon.getTags(),
						osmPolygon.getId())
				&& !specialExcluded(osmPolygon)				
				&& osmPolygon.getPolygon().vertexNumber() > BUILDING_MIN_VECTORS
				&& osmPolygon.getPolygon().vertexNumber() < BUILDING_MAX_VECTORS) { 
	
			// check that the largest vector of the building
			// and that the area of the osmPolygon.getPolygon() are over the
			// minimum values set by the user
			Double maxVector = osmPolygon.getMaxVectorSize();
			if (maxVector > XPlaneOptionsProvider.getOptions()
					.getMinHouseSegment()
					&& maxVector < XPlaneOptionsProvider.getOptions()
							.getMaxHouseSegment()
					&& ((osmPolygon.getPolygon().area() * 100000) * 100000) > XPlaneOptionsProvider
							.getOptions().getMinHouseArea()) {
	
				// simplify shape if checked and if necessary
				if (GlobalOptionsProvider.getOptions().isSimplifyShapes()
						&& !osmPolygon.isSimplePolygon()) {
					osmPolygon = osmPolygon.toSimplifiedPoly();
				}
	
				// compute height and facade dsf index
				osmPolygon.setHeight(computeBuildingHeight(osmPolygon));
				Integer facade = computeFacadeIndex(osmPolygon);
				if (translationListener != null) {
					translationListener.processBuilding(osmPolygon, facade);
				}
				
				// write building in dsf file
				writeBuildingToDsf(osmPolygon, facade);
				// Smart exclusions
				if (XPlaneOptionsProvider.getOptions().isSmartExclusions()) {
					exclusionsHelper.addTodoPolygon(osmPolygon);
					exclusionsHelper.run();
				}
				result = true;
			}
		}
		return result;
	}
}
