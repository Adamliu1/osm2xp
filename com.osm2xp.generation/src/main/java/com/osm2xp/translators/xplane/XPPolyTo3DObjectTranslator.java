package com.osm2xp.translators.xplane;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.onpositive.classification.core.buildings.OSMBuildingType;
import com.onpositive.classification.core.buildings.TypeProvider;
import com.osm2xp.core.logging.Osm2xpLogger;
import com.osm2xp.generation.options.XPlaneOptionsProvider;
import com.osm2xp.generation.paths.PathsService;
import com.osm2xp.model.osm.polygon.OsmPolygon;
import com.osm2xp.model.osm.polygon.OsmPolyline;
import com.osm2xp.model.xplane.ModelWithSize;
import com.osm2xp.utils.DsfObjectsProvider;
import com.osm2xp.utils.geometry.GeomUtils;
import com.osm2xp.writers.IWriter;

import math.geom2d.Point2D;
import math.geom2d.line.LineSegment2D;


/**
 * This translator loads models with specified size (like house_10x10.obj) from xplane/objects/ folder subfolders and tries to select suitable model by type and size
 * For supported building types, see {@link OSMBuildingType}  
 * @author 32kda
 *
 */
public class XPPolyTo3DObjectTranslator extends XPWritingTranslator {

	private Multimap<OSMBuildingType, ModelWithSize> modelsByType = ArrayListMultimap.create();
	private DsfObjectsProvider dsfObjectsProvider;
	private XPOutputFormat outputFormat;
	
	public XPPolyTo3DObjectTranslator(IWriter writer, DsfObjectsProvider dsfObjectsProvider, XPOutputFormat outputFormat) {
		super(writer);
		this.dsfObjectsProvider = dsfObjectsProvider;
		this.outputFormat = outputFormat;
		File objectsFolder = PathsService.getPathsProvider().getObjectsFolder();
		for (OSMBuildingType type : OSMBuildingType.values()) {
			File folder = new File(objectsFolder, type.name().toLowerCase());
			modelsByType.putAll(type,getFromDirectory(objectsFolder.getName() + "/" + folder.getName(), folder));
		}
	}

	protected List<ModelWithSize> getFromDirectory(String preffixPath, File parentFolder) {
		if (!parentFolder.isDirectory()) {
			return Collections.emptyList();
		}
		List<ModelWithSize> resList = new ArrayList<ModelWithSize>();
		File[] files = parentFolder.listFiles((dir,name) -> name.endsWith(DsfObjectsProvider.OBJ_EXT));
		resList.addAll(Arrays.asList(files).stream().map(file -> createFromFileName(preffixPath, file.getName())).filter(model -> model != null).collect(Collectors.toList()));
		
		File[] folders = parentFolder.listFiles(file -> file.isDirectory());
		for (File folder : folders) {
			resList.addAll(getFromDirectory(preffixPath + "/" + folder.getName(), folder));
		}
		return resList;
	}

	@Override
	public boolean handlePoly(OsmPolyline osmPolyline) {
		if (!XPlaneOptionsProvider.getOptions().isGenerateObjBuildings()) {
			return false;
		}
		if (!(osmPolyline instanceof OsmPolygon)) {
			return false;
		}
		if (!((OsmPolygon) osmPolyline).isSimplePolygon()) {
			double length = GeomUtils.computeEdgesLength(osmPolyline.getPolyline());
			if (length <= XPlaneOptionsProvider.getOptions().getMaxPerimeterToSimplify()) { //If house is small enough, we don't care about complex shape and just simplify it to try finding suitable model
				osmPolyline = ((OsmPolygon) osmPolyline).toSimplifiedPoly();
			} else {
				return false;
			}
		}
		double tolerance = XPlaneOptionsProvider.getOptions().getObjSizeTolerance();
		OSMBuildingType buildingType = TypeProvider.getBuildingType(osmPolyline.getTags());
		LineSegment2D edge0 = osmPolyline.getPolyline().edge(0);
		LineSegment2D edge1 = osmPolyline.getPolyline().edge(1);
		LineSegment2D edge2 = osmPolyline.getPolyline().edge(2);
		LineSegment2D edge3 = osmPolyline.getPolyline().edge(3);
		if (buildingType != null) {
			Collection<ModelWithSize> models = modelsByType.get(buildingType);
			if (models.isEmpty()) {
				return false;
			}
			double len1 = GeomUtils.computeAvgDistance(edge0,edge2);
			double len2 = GeomUtils.computeAvgDistance(edge1,edge3);
			double dist = Double.MAX_VALUE;
			ModelWithSize matched = null;
			boolean directAngle = true;
			for (ModelWithSize model : models) {
				double dist1 = GeomUtils.fitWithDistance(model.geXSize(), model.getYSize(),tolerance,len1,len2);
				double dist2 = GeomUtils.fitWithDistance(model.geXSize(), model.getYSize(),tolerance,len2,len1);
				if (dist1 < dist || dist2 < dist) {
					directAngle = dist1 < dist2;
					dist = Math.min(dist1, dist2);
					matched = model;
				} 
			}
			if (matched != null) {
				Point2D center = GeomUtils.getPolylineCenter(osmPolyline.getPolyline());
				double angle = directAngle? GeomUtils.getTrueBearing(edge1.firstPoint(), edge1.lastPoint()) : 
											GeomUtils.getTrueBearing(edge0.firstPoint(), edge0.lastPoint());
				double d = Math.random();
				angle = d < 0.5 ? angle : (angle + 180) % 360;
				String objectString = outputFormat.getObjectString(dsfObjectsProvider.getObjectIndex(matched.getPath()),center.x(), center.y(), angle);
				writer.write(objectString);
				return true;
			}
		}
		return false;
	}

	@Override
	public void translationComplete() {
		// Do nothing
	}

	@Override
	public String getId() {
		return "polygon_to_object";
	}
	
	protected ModelWithSize createFromFileName(String preffixPath, String fileName) {
		int idx = 0;
		int n = fileName.length() - DsfObjectsProvider.OBJ_EXT.length();
		while (idx < n) {
			if (Character.isDigit(fileName.charAt(idx))) {
				int start = idx;
				while (idx < n && (Character.isDigit(fileName.charAt(idx)) || fileName.charAt(idx) == 'x' || fileName.charAt(idx) == '.')) {
					idx++;
				}
				idx--;
				while(idx > 0 && !Character.isDigit(fileName.charAt(idx))) { //Skip possible tail until we see a number;
					idx--;
				}
				idx++;
				String marking = fileName.substring(start, idx);
				String[] parts = marking.split("x");
				if (parts.length == 2) {
					try {
						double x = Double.parseDouble(parts[0]);
						double y = Double.parseDouble(parts[1]);
						return new ModelWithSize(preffixPath + "/" + fileName, x ,y);
					} catch (NumberFormatException e) {
						Osm2xpLogger.error(e);
					}
				}
			} else {
				idx++;
			}
		}
		return null;
	}

}