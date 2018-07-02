package com.osm2xp.translators.xplane;

import static com.osm2xp.translators.impl.XPlaneTranslatorImpl.LINE_SEP;

import com.osm2xp.model.facades.BarrierType;
import com.osm2xp.model.osm.OsmPolygon;
import com.osm2xp.model.osm.OsmPolyline;
import com.osm2xp.translators.impl.XPOutputFormat;
import com.osm2xp.utils.DsfObjectsProvider;
import com.osm2xp.utils.GeomUtils;
import com.osm2xp.utils.helpers.XplaneOptionsHelper;
import com.osm2xp.writers.IWriter;

import math.geom2d.polygon.LinearRing2D;

public class XPBarrierTranslator extends XPWritingTranslator {

	private static final Double MIN_BARRIER_PERIMETER = 200.0; //TODO make configurable from UI
	private DsfObjectsProvider dsfObjectsProvider;
	private XPOutputFormat outputFormat;

	public XPBarrierTranslator(IWriter writer, DsfObjectsProvider dsfObjectsProvider, XPOutputFormat outputFormat) {
		super(writer);
		this.dsfObjectsProvider = dsfObjectsProvider;
		this.outputFormat = outputFormat;
	}

	@Override
	public boolean handlePoly(OsmPolyline osmPolyline) {
		if (osmPolyline.getId() == 226562104) {
 			System.out.println("XPBarrierTranslator.handlePoly()");
		}
		if (!XplaneOptionsHelper.getOptions().isGenerateFence() || osmPolyline.isPartial()) {
			return false;
		}
		String barrierType = osmPolyline.getTagValue("barrier");
		if (barrierType != null && GeomUtils.computeEdgesLength(osmPolyline.getPolyline()) > MIN_BARRIER_PERIMETER && osmPolyline.isValid()) {
			Integer facade = dsfObjectsProvider.getRandomBarrierFacade(getBarrierType(barrierType),osmPolyline);
			if (facade != null && facade >= 0) {
				StringBuffer sb = new StringBuffer();
				if (XplaneOptionsHelper.getOptions().isGenerateComments()) {
					sb.append("#Barrier " + barrierType + " facade " + facade);
					sb.append(LINE_SEP);
				}
				if (osmPolyline instanceof OsmPolygon) {
					((OsmPolygon)osmPolyline).setPolygon(GeomUtils.setCCW((LinearRing2D) osmPolyline
						.getPolyline()));
				}
				
				sb.append(outputFormat.getPolygonString(osmPolyline, facade + "", "2")); //TODO need actual wall height here, using "2" for now
//				sb.append("BEGIN_POLYGON " + facade + " 2 2"); //TODO need actual wall height here, using "2" for now
//				sb.append(LINE_SEP);
//				sb.append("BEGIN_WINDING");
//				sb.append(LINE_SEP);
//	
////				osmPolygon.getPolygon().removePoint(
////						osmPolygon.getPolygon().getLastPoint());
//				for (Point2D loc : osmPolyline.getPolyline().getVertices()) {
//					sb.append("POLYGON_POINT " + loc.x + " " + loc.y);
//					sb.append(LINE_SEP);
//				}
//				sb.append("END_WINDING");
//				sb.append(LINE_SEP);
//				sb.append("END_POLYGON");
//				sb.append(LINE_SEP);
				writer.write(sb.toString(), GeomUtils
						.cleanCoordinatePoint(osmPolyline.getPolyline()
								.getFirstPoint()));
			}
			return true;
		}
		return false;
	}

	private BarrierType getBarrierType(String barrierTypeStr) {
		if ("wall".equalsIgnoreCase(barrierTypeStr)) {
			return BarrierType.WALL;
		}
		return BarrierType.FENCE;
	}

	@Override
	public void translationComplete() {
		// Do nothing
	}

}
