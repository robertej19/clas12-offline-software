/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jlab.clas.swimtools;

import cnuphys.magfield.RotatedCompositeProbe;
import cnuphys.rk4.IStopper;
import cnuphys.rk4.RungeKuttaException;
import cnuphys.swim.StateVec;
import cnuphys.swim.SwimException;
import cnuphys.swim.SwimTrajectory;
import cnuphys.swim.Trajectory;
import cnuphys.swim.util.Plane;
import cnuphys.swimZ.SwimZ;
import cnuphys.swimS.SwimS;

import org.apache.commons.math3.util.FastMath;
import org.jlab.geom.prim.Vector3D;

/**
 *
 * @author ziegler
 */

public class Swim {

	private double _x0;
	private double _y0;
	private double _z0;
	private double _phi;
	private double _theta;
	private double _pTot;
	private double _rMax = 5 + 3; // increase to allow swimming to outer
									// detectors
	private double _maxPathLength = 9;
	private int _charge;
	
	//if true, charge was flipped to reverse swim
	private boolean _chargeFlipped = false;

	final double SWIMZMINMOM = 0.75; // GeV/c
	final double MINTRKMOM = 0.05; // GeV/c
	final double accuracy = 20e-6; // 20 microns
	final double stepSize = 5.00 * 1.e-4; // 500 microns

	private ProbeCollection PC;

	public Swim() {
		PC = Swimmer.getProbeCollection(Thread.currentThread());
		if (PC == null) {
			PC = new ProbeCollection();
			Swimmer.put(Thread.currentThread(), PC);
		}

	}
	
	/**
	 * Get the rotated composite probe for this thread
	 * @return the rotated composite probe for this thread
	 */
	public RotatedCompositeProbe getRCP() {
		return PC.RCP;
	}
	
	/**
	 * Get the rotated composite Z swimmer for this thread
	 * @return the rotated composite Z swimmer for this thread
	 */
	public SwimZ getRCF_z() {
		return PC.RCF_z;
	}

	/**
	 * Get the rotated composite S swimmer for this thread
	 * @return the rotated composite S swimmer for this thread
	 */
	public SwimS getRCF_s() {
		return PC.RCF_s;
	}


	/**
	 *
	 * @param direction
	 *            +1 for out -1 for in
	 * @param x0
	 * @param y0
	 * @param z0
	 * @param thx
	 * @param thy
	 * @param p
	 * @param charge
	 */
	public void SetSwimParameters(int direction, double x0, double y0, double z0, double thx, double thy, double p,
			int charge) {

		// x,y,z in m = swimmer units
		_x0 = x0 / 100;
		_y0 = y0 / 100;
		_z0 = z0 / 100;

		double pz = direction * p / Math.sqrt(thx * thx + thy * thy + 1);
		double px = thx * pz;
		double py = thy * pz;
		_phi = Math.toDegrees(FastMath.atan2(py, px));
		_pTot = Math.sqrt(px * px + py * py + pz * pz);
		_theta = Math.toDegrees(Math.acos(pz / _pTot));
 		_charge = direction * charge;
 		
 		_chargeFlipped = (direction < 0);
	}

	/**
	 * Sets the parameters used by swimmer based on the input track state vector
	 * parameters swimming outwards
	 *
	 * @param superlayerIdx
	 * @param layerIdx
	 * @param x0
	 * @param y0
	 * @param thx
	 * @param thy
	 * @param p
	 * @param charge
	 */
	public void SetSwimParameters(int superlayerIdx, int layerIdx, double x0, double y0, double z0, double thx,
			double thy, double p, int charge) {
		// z at a given DC plane in the tilted coordinate system
		// x,y,z in m = swimmer units
		_x0 = x0 / 100;
		_y0 = y0 / 100;
		_z0 = z0 / 100;

		double pz = p / Math.sqrt(thx * thx + thy * thy + 1);
		double px = thx * pz;
		double py = thy * pz;
		_phi = Math.toDegrees(FastMath.atan2(py, px));
		_pTot = Math.sqrt(px * px + py * py + pz * pz);
		_theta = Math.toDegrees(Math.acos(pz / _pTot));

		_charge = charge;

		_chargeFlipped = false;
	}

	/**
	 * Sets the parameters used by swimmer based on the input track parameters
	 *
	 * @param x0
	 * @param y0
	 * @param z0
	 * @param px
	 * @param py
	 * @param pz
	 * @param charge
	 */
	public void SetSwimParameters(double x0, double y0, double z0, double px, double py, double pz, int charge) {
		_x0 = x0 / 100;
		_y0 = y0 / 100;
		_z0 = z0 / 100;
		_phi = Math.toDegrees(FastMath.atan2(py, px));
		_pTot = Math.sqrt(px * px + py * py + pz * pz);
		_theta = Math.toDegrees(Math.acos(pz / _pTot));

		_charge = charge;
		_chargeFlipped = false;
	}

	/**
	 * 
	 * @param xcm
	 * @param ycm
	 * @param zcm
	 * @param phiDeg
	 * @param thetaDeg
	 * @param p
	 * @param charge
	 * @param maxPathLength
	 */
	public void SetSwimParameters(double xcm, double ycm, double zcm, double phiDeg, double thetaDeg, double p,
			int charge, double maxPathLength) {

		_maxPathLength = maxPathLength;
		_charge = charge;
		_phi = phiDeg;
		_theta = thetaDeg;
		_pTot = p;
		_x0 = xcm / 100;
		_y0 = ycm / 100;
		_z0 = zcm / 100;
		_chargeFlipped = false;
	}

	public double[] SwimToPlaneTiltSecSys(int sector, double z_cm) {
		double z = z_cm / 100; // the magfield method uses meters
		double[] value = new double[8];

		if (_pTot < MINTRKMOM) // fiducial cut
		{
			return value;
		}

		// use a SwimZResult instead of a trajectory (dph)
		Trajectory szr = null;

		SwimTrajectory traj = null;
		double hdata[] = new double[3];

		try {

			if (_pTot > SWIMZMINMOM) {

				// use the new z swimmer (dph)
				// NOTE THE DISTANCE, UNITS FOR swimZ are cm, NOT m like the old
				// swimmer (dph)

				double stepSizeCM = stepSize * 100; // convert to cm

				// create the starting SwimZ state vector
				StateVec start = new StateVec(_x0 * 100, _y0 * 100, _z0 * 100, _charge/_pTot, _theta, _phi);
				
				boolean signConsistent = start.pzSignCorrect(_z0 * 100, z_cm);
				if (!signConsistent) {
//					System.out.println("INCONSISTENT sign for pz zi = " + (_z0 * 100) + "   zf = " + z_cm + 
//							"  SIGN: " + start.pzSign + "   theta = " + _theta + "   phi = " + _phi);
//					
					for (int i = 0; i < value.length; i++) {
						value[i] = 0;
						return value;
					}
				}

				try {
					szr = PC.RCF_z.sectorAdaptiveRK(sector, start, z_cm, stepSizeCM, hdata);
				} catch (SwimException e) {
					szr = null;
					System.err.println("[WARNING] Tilted SwimZ Failed for p = " + _pTot);
				}
			}

			if (szr != null) {
				double bdl = szr.sectorGetBDL(sector, PC.RCF_z.getProbe());
				double pathLength = szr.getPathLength(); // already in cm

				StateVec last = szr.last();
				double p3[] = last.getThreeMomentum();

				value[0] = last.x; // xf in cm
				value[1] = last.y; // yz in cm
				value[2] = last.z; // zf in cm
				value[3] = p3[0];
				value[4] = p3[1];
				value[5] = p3[2];
				value[6] = pathLength;
				value[7] = bdl / 10; // convert from kg*cm to T*cm
			} else { // use old swimmer. Either low momentum or SwimZ failed.
						// (dph)

				traj = PC.RCF.sectorSwim(sector, _charge, _x0, _y0, _z0, _pTot, _theta, _phi, z, accuracy, _rMax,
						_maxPathLength, stepSize, cnuphys.swim.Swimmer.CLAS_Tolerance, hdata);

				// traj.computeBDL(sector, rprob);
				traj.sectorComputeBDL(sector, PC.RCP);
				// traj.computeBDL(rcompositeField);

				double lastY[] = traj.lastElement();
				value[0] = lastY[0] * 100; // convert back to cm
				value[1] = lastY[1] * 100; // convert back to cm
				value[2] = lastY[2] * 100; // convert back to cm
				value[3] = lastY[3] * _pTot;
				value[4] = lastY[4] * _pTot;
				value[5] = lastY[5] * _pTot;
				value[6] = lastY[6] * 100;
				value[7] = lastY[7] * 10;
			} // use old swimmer
		} catch (Exception e) {
			e.printStackTrace();
		}
		return value;

	}

	public double[] SwimToPlaneLab(double z_cm) {
		double z = z_cm / 100; // the magfield method uses meters
		double[] value = new double[8];

		if (_pTot < MINTRKMOM) // fiducial cut
		{
			return null;
		}
		SwimTrajectory traj = null;
		double hdata[] = new double[3];

		// use a SwimZResult instead of a trajectory (dph)
		Trajectory szr = null;

		try {

			if (_pTot > SWIMZMINMOM) {

				// use the new z swimmer (dph)
				// NOTE THE DISTANCE, UNITS FOR swimZ are cm, NOT m like the old
				// swimmer (dph)

				double stepSizeCM = stepSize * 100; // convert to cm

				// create the starting SwimZ state vector
				StateVec start = new StateVec(_x0 * 100, _y0 * 100, _z0 * 100, _charge/_pTot, _theta, _phi);
				
				boolean signConsistent = start.pzSignCorrect(_z0 * 100, z_cm);
				if (!signConsistent) {
//					System.out.println("INCONSISTENT sign for pz zi = " + (_z0 * 100) + "   zf = " + z_cm + 
//							"  SIGN: " + start.pzSign + "   theta = " + _theta + "   phi = " + _phi);
//					
					for (int i = 0; i < value.length; i++) {
						value[i] = 0;
						return value;
					}
				}


				try {
					szr = PC.CF_z.adaptiveRK(start, z_cm, stepSizeCM, hdata);
				} catch (SwimException e) {
					szr = null;
					System.err.println("[WARNING] SwimZ Failed for p = " + _pTot);

				}
			}

			if (szr != null) {
				double bdl = szr.getBDL(PC.CF_z.getProbe());
				double pathLength = szr.getPathLength(); // already in cm

				StateVec last = szr.last();
				double p3[] = last.getThreeMomentum();

				value[0] = last.x; // xf in cm
				value[1] = last.y; // yz in cm
				value[2] = last.z; // zf in cm
				value[3] = p3[0];
				value[4] = p3[1];
				value[5] = p3[2];
				value[6] = pathLength;
				value[7] = bdl / 10; // convert from kg*cm to T*cm
			} else { // use old swimmer. Either low momentum or SwimZ failed.
						// (dph)
				traj = PC.CF.swim(_charge, _x0, _y0, _z0, _pTot, _theta, _phi, z, accuracy, _rMax, _maxPathLength,
						stepSize, cnuphys.swim.Swimmer.CLAS_Tolerance, hdata);

				traj.computeBDL(PC.CP);
				// traj.computeBDL(compositeField);

				double lastY[] = traj.lastElement();

				value[0] = lastY[0] * 100; // convert back to cm
				value[1] = lastY[1] * 100; // convert back to cm
				value[2] = lastY[2] * 100; // convert back to cm
				value[3] = lastY[3] * _pTot;
				value[4] = lastY[4] * _pTot;
				value[5] = lastY[5] * _pTot;
				value[6] = lastY[6] * 100;
				value[7] = lastY[7] * 10;
			} // old swimmer

		} catch (RungeKuttaException e) {
			e.printStackTrace();
		}
		return value;

	}

	private class CylindricalcalBoundarySwimStopper implements IStopper {

		private double _finalPathLength = Double.NaN;

		private double _Rad;

		/**
		 * A swim stopper that will stop if the boundary of a plane is crossed
		 *
		 * @param maxR
		 *            the max radial coordinate in meters.
		 */
		private CylindricalcalBoundarySwimStopper(double Rad) {
			// DC reconstruction units are cm. Swim units are m. Hence scale by
			// 100
			_Rad = Rad;
		}

		@Override
		public boolean stopIntegration(double t, double[] y) {

			double r = Math.sqrt(y[0] * y[0] + y[1] * y[1]) * 100.;

			return (r > _Rad);

		}

		/**
		 * Get the final path length in meters
		 *
		 * @return the final path length in meters
		 */
		@Override
		public double getFinalT() {
			return _finalPathLength;
		}

		/**
		 * Set the final path length in meters
		 *
		 * @param finalPathLength
		 *            the final path length in meters
		 */
		@Override
		public void setFinalT(double finalPathLength) {
			_finalPathLength = finalPathLength;
		}
	}

	public double[] SwimToCylinder(double Rad) {

		double[] value = new double[8];
		// using adaptive stepsize

		SphericalBoundarySwimStopper stopper = new SphericalBoundarySwimStopper(Rad);

		SwimTrajectory st = PC.CF.swim(_charge, _x0, _y0, _z0, _pTot, _theta, _phi, stopper, _maxPathLength, stepSize,
				0.0005);
		st.computeBDL(PC.CP);
		// st.computeBDL(compositeField);

		double[] lastY = st.lastElement();

		value[0] = lastY[0] * 100; // convert back to cm
		value[1] = lastY[1] * 100; // convert back to cm
		value[2] = lastY[2] * 100; // convert back to cm
		value[3] = lastY[3] * _pTot; // normalized values
		value[4] = lastY[4] * _pTot;
		value[5] = lastY[5] * _pTot;
		value[6] = lastY[6] * 100;
		value[7] = lastY[7] * 10; // Conversion from kG.m to T.cm

		return value;

	}

	private class SphericalBoundarySwimStopper implements IStopper {

		private double _finalPathLength = Double.NaN;

		private double _Rad;

		/**
		 * A swim stopper that will stop if the boundary of a plane is crossed
		 *
		 * @param maxR
		 *            the max radial coordinate in meters.
		 */
		private SphericalBoundarySwimStopper(double Rad) {
			// DC reconstruction units are cm. Swim units are m. Hence scale by
			// 100
			_Rad = Rad;
		}

		@Override
		public boolean stopIntegration(double t, double[] y) {

			double r = Math.sqrt(y[0] * y[0] + y[1] * y[1] + y[2] * y[2]) * 100.;

			return (r > _Rad);

		}

		/**
		 * Get the final path length in meters
		 *
		 * @return the final path length in meters
		 */
		@Override
		public double getFinalT() {
			return _finalPathLength;
		}

		/**
		 * Set the final path length in meters
		 *
		 * @param finalPathLength
		 *            the final path length in meters
		 */
		@Override
		public void setFinalT(double finalPathLength) {
			_finalPathLength = finalPathLength;
		}
	}

	public double[] SwimToSphere(double Rad) {

		double[] value = new double[8];
		// using adaptive stepsize

		SphericalBoundarySwimStopper stopper = new SphericalBoundarySwimStopper(Rad);

		SwimTrajectory st = PC.CF.swim(_charge, _x0, _y0, _z0, _pTot, _theta, _phi, stopper, _maxPathLength, stepSize,
				0.0005);
		st.computeBDL(PC.CP);
		// st.computeBDL(compositeField);

		double[] lastY = st.lastElement();

		value[0] = lastY[0] * 100; // convert back to cm
		value[1] = lastY[1] * 100; // convert back to cm
		value[2] = lastY[2] * 100; // convert back to cm
		value[3] = lastY[3] * _pTot; // normalized values
		value[4] = lastY[4] * _pTot;
		value[5] = lastY[5] * _pTot;
		value[6] = lastY[6] * 100;
		value[7] = lastY[7] * 10; // Conversion from kG.m to T.cm

		return value;

	}

	// added for swimming to outer detectors
	private class PlaneBoundarySwimStopper implements IStopper {

		private double _finalPathLength = Double.NaN;
		private double _d;
		private Vector3D _n;
		private double _dist2plane;
		private int _dir;

		/**
		 * A swim stopper that will stop if the boundary of a plane is crossed
		 *
		 * @param maxR
		 *            the max radial coordinate in meters.
		 */
		private PlaneBoundarySwimStopper(double d, Vector3D n, int dir) {
			// DC reconstruction units are cm. Swim units are m. Hence scale by
			// 100
			_d = d;
			_n = n;
			_dir = dir;
		}

		@Override
		public boolean stopIntegration(double t, double[] y) {
			double dtrk = y[0] * _n.x() + y[1] * _n.y() + y[2] * _n.z();

			double accuracy = 20e-6; // 20 microns
			// System.out.println(" dist "+dtrk*100+ " state "+y[0]*100+",
			// "+y[1]*100+" , "+y[2]*100);
			if (_dir < 0) {
				return dtrk < _d;
			} else {
				return dtrk > _d;
			}

		}

		@Override
		public double getFinalT() {

			return _finalPathLength;
		}

		/**
		 * Set the final path length in meters
		 *
		 * @param finalPathLength
		 *            the final path length in meters
		 */
		@Override
		public void setFinalT(double finalPathLength) {
			_finalPathLength = finalPathLength;
		}
	}

	public double[] SwimToPlaneBoundary(double d_cm, Vector3D n, int dir) {

		double[] value = new double[8];
		double d = d_cm / 100;

		double hdata[] = new double[3];
		// using adaptive stepsize

		// the new swim to plane in swimmer
		Plane plane = new Plane(n.x(), n.y(), n.z(), d);
		SwimTrajectory st;
		try {

			// swim backwards?
			double vertexR = Math.sqrt(_x0 * _x0 + _y0 * _y0 + _z0 * _z0);
			if ((vertexR > d) && (dir > 0)) {

				//trying to swim forward, but already beyond the plane. Just return the starting values
				double thetaRad = Math.toRadians(_theta);
				double phiRad = Math.toRadians(_phi);
				double pz = _pTot * Math.cos(thetaRad);
				double pperp = _pTot * Math.sin(thetaRad);
				double px = pperp * Math.cos(phiRad);
				double py = pperp * Math.sin(phiRad);

				value[0] = _x0 * 100; // convert back to cm
				value[1] = _y0 * 100; // convert back to cm
				value[2] = _z0 * 100; // convert back to cm

				value[3] = px;
				value[4] = py;
				value[5] = pz;
				value[6] = 0;
				value[7] = 0;

				return value;

			}

			else {
				st = PC.CF.swim(_charge, _x0, _y0, _z0, _pTot, _theta, _phi, plane, accuracy, _maxPathLength, stepSize,
						cnuphys.swim.Swimmer.CLAS_Tolerance, hdata);

				st.computeBDL(PC.CP);

				double[] lastY = st.lastElement();

				value[0] = lastY[0] * 100; // convert back to cm
				value[1] = lastY[1] * 100; // convert back to cm
				value[2] = lastY[2] * 100; // convert back to cm
				value[3] = lastY[3] * _pTot; // normalized values
				value[4] = lastY[4] * _pTot;
				value[5] = lastY[5] * _pTot;
				value[6] = lastY[6] * 100;
				value[7] = lastY[7] * 10; // Conversion from kG.m to T.cm

				// System.out.println("\nCOMPARE plane swims DIRECTION = " +
				// dir);
				// for (int i = 0; i < 8; i++) {
				// System.out.print(String.format("%-8.5f ", value[i]));
				// }

			}
		} catch (RungeKuttaException e) {
			e.printStackTrace();
		}

//		PlaneBoundarySwimStopper stopper = new PlaneBoundarySwimStopper(d, n, dir);

		// this is a uniform stepsize swimmer (dph)
//		st = PC.CF.swim(_charge, _x0, _y0, _z0, _pTot, _theta, _phi, stopper, _maxPathLength, stepSize, 0.0005);
//		st.computeBDL(PC.CP);
//		// st.computeBDL(compositeField);
//
//		double[] lastY = st.lastElement();
//
//		value[0] = lastY[0] * 100; // convert back to cm
//		value[1] = lastY[1] * 100; // convert back to cm
//		value[2] = lastY[2] * 100; // convert back to cm
//		value[3] = lastY[3] * _pTot; // normalized values
//		value[4] = lastY[4] * _pTot;
//		value[5] = lastY[5] * _pTot;
//		value[6] = lastY[6] * 100;
//		value[7] = lastY[7] * 10; // Conversion from kG.m to T.cm
//
//		double tv1 = Math.abs(tvalue[0]);
//		double v1 = Math.abs(value[0]);
//
//		double fract = (Math.abs(tv1 - v1) / Math.max(tv1, v1));
//		if (fract > 0.9) {
//			System.out.println("\nBig Diff fract = " + fract + "   direction = " + dir + "   DIST = " + d);
//
//			double vtxR = Math.sqrt(_x0 * _x0 + _y0 * _y0 + _z0 * _z0);
//			System.out.println("VTX: (" + _x0 + ", " + _y0 + ", " + _z0 + ") VtxR: " + vtxR + "   P: " + _pTot
//					+ "  theta: " + _theta + "  phi: " + _phi);
//
//			printV("tV", tvalue);
//			printV(" V", value);
//			// System.out.println("tV: (" + tvalue[0]/100 + ", " + tvalue[1]/100
//			// + ", " + tvalue[2]/100 + ")");
//			// System.out.println(" V: (" + value[0]/100 + ", " + value[1]/100 +
//			// ", " + value[2]/100 + ")");
//		}
//
//		// System.out.println();
//		// for (int i = 0; i < 8; i++) {
//		// System.out.print(String.format("%-8.5f ", value[i]));
//		// }
//		// System.out.println();

		return value;

	}

	private void printV(String pfx, double v[]) {
		double x = v[0] / 100;
		double y = v[1] / 100;
		double z = v[2] / 100;
		double r = Math.sqrt(x * x + y * y + z * z);
		System.out.println(String.format("%s: (%-8.5f, %-8.5f, %-8.5f) R: %-8.5f", pfx, z, y, z, r));
	}

	public void Bfield(int sector, double x_cm, double y_cm, double z_cm, float[] result) {

		PC.RCP.field(sector, (float) x_cm, (float) y_cm, (float) z_cm, result);
		// rcompositeField.field((float) x_cm, (float) y_cm, (float) z_cm,
		// result);
		result[0] = result[0] / 10;
		result[1] = result[1] / 10;
		result[2] = result[2] / 10;

	}

	public void BfieldLab(double x_cm, double y_cm, double z_cm, float[] result) {

		PC.CP.field((float) x_cm, (float) y_cm, (float) z_cm, result);
		result[0] = result[0] / 10;
		result[1] = result[1] / 10;
		result[2] = result[2] / 10;

	}
	
	/**
	 * Check whether the charge was flipped for a retrace swim
	 * @return <code>true</code> if the charge was flipped for a retrace swim
	 */
	public boolean isChargeFlipped() {
		return _chargeFlipped;
	}
	
	

}