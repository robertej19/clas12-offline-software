/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jlab.geom.detector.ftof;

import org.jlab.geom.DetectorId;
import org.jlab.geom.abs.AbstractSuperlayer;

/**
 *
 * @author gavalian
 */
public class FTOFSuperlayerMesh extends AbstractSuperlayer<FTOFLayerMesh>{

    protected FTOFSuperlayerMesh(int sectorId, int superlayerId) {
        super(DetectorId.FTOF, sectorId, superlayerId);
    }
    
    @Override
    public String getType() {
        return "FTOF superlayer";
    }
    
}
