package com.badrobots.y2012.technetium.subsystems;

import edu.wpi.first.wpilibj.Joystick;
import edu.wpi.first.wpilibj.RobotDrive;
import edu.wpi.first.wpilibj.Victor;
import com.badrobots.y2012.technetium.commands.MechanumDrive;
import edu.wpi.first.wpilibj.command.Subsystem;
import com.badrobots.y2012.technetium.OI;
import com.badrobots.y2012.technetium.PacketListener;
import com.badrobots.y2012.technetium.RobotMap;
import com.badrobots.y2012.technetium.commands.TankDrive;
import edu.wpi.first.wpilibj.*;

/*
 * @author 1014 Programming Team
 */
public class Hermes extends Subsystem
{//tinychange

    private static Hermes instance;
    private static RobotDrive drive;
    public Jaguar lFront, lBack, rFront, rBack;
    private Gyro horizontalGyro;
    private Accelerometer accel;
    protected static double strafeCorrectionFactor = .165;
    protected static double oneForOneDepth = 5000; // millimeters
    private SoftPID rotationPID;
    private PIDController pidController;
    private double requestedAngle = 0;
    private double orientation = 1;
    private boolean changeDirection = false;
    private boolean PIDControl = false;
    private boolean toggleButton = false;
    private boolean buttonTogglePIDOff = false;
    private double rotation;
    private double wantedAngle;

    /**
     * Singleton Design getter method -- ensures that only one instance of
     * DriveTrain is every used. If one has not been made, this method also
     * invokes the constructor
     *
     * @return the single instance of DriveTrain per program
     */
    public static Hermes getInstance()
    {
        if (instance == null)
        {
            instance = new Hermes();
        }
        return instance;
    }

    /*
     * Initailizes four Victors, feeds them into a RobotDrive instance, and sets
     * the motors in RobotDrive to the correct running direction.
     */
    private Hermes()
    {
        super();

        lFront = new Jaguar(RobotMap.lFront);   //initializes all victors
        lBack = new Jaguar(RobotMap.lBack);
        rFront = new Jaguar(RobotMap.rFront);
        rBack = new Jaguar(RobotMap.rBack);

        drive = new RobotDrive(lFront, lBack, rFront, rBack);   // feeds victors to RobotDrive

        drive.setInvertedMotor(RobotDrive.MotorType.kFrontRight, true);
        //drive.setInvertedMotor(RobotDrive.MotorType.kFrontLeft, true); 
        drive.setInvertedMotor(RobotDrive.MotorType.kRearRight, true);
        //drive.setInvertedMotor(RobotDrive.MotorType.kRearLeft, true); //
        if (OI.PIDOn)
        {
            horizontalGyro = new Gyro(RobotMap.horizontalGyro); //that's wrong
        }
        drive.setSafetyEnabled(false);
        //because why not. Jon: because it will kill us all. 
        // Haven't you seen iRobot? They left their robots on
        // safety enable = false

        //Rotation and angle requesting stuff for PID
        rotation = 0;
        if (horizontalGyro != null)
        {
            rotationPID = new SoftPID();
            //TODO: Set these to constants
            pidController = new PIDController(OI.getAnalogIn(1), OI.getAnalogIn(2), OI.getAnalogIn(3), horizontalGyro, rotationPID);
            pidController.setTolerance(.05);
        }
    }

    /*
     * Takes in 3 values from the joysticks, and sends voltages to
     * speedcontrollers accordingly Status:Tested
     */
    public void mechanumDrive()
    {
        double strafeX = 0;
        double strafeY = 0;
        double scaledTurn = 0;
        double leftX = OI.getUsedLeftX();
        double leftY = OI.getUsedLeftY();
        double rightX = OI.getUsedRightX();
        double rightY = OI.getUsedRightY();
        double sensitivity = OI.getSensitivity();
        boolean useRightJoystickForStrafe = OI.rightStrafe();

        //correct for strafing code
        //if right hand stick is being used for strafing left, right, up and down
        if (useRightJoystickForStrafe)
        {
            strafeY = rightY * sensitivity;
            strafeX = rightX * 1.25 * sensitivity;
            strafeX = clampMotorValues(strafeX);
            scaledTurn = (rightX + (strafeCorrectionFactor * strafeX)) * sensitivity;
        }
        else
        {
            strafeY = leftY * sensitivity;
            strafeX = leftX * 1.25 * sensitivity;
            strafeX = clampMotorValues(strafeX);
            scaledTurn = (leftX + (strafeCorrectionFactor * strafeX)) * sensitivity;
        }
        checkForOrientationChange();

        //apply PID if it should
        scaledTurn = checkAndRunPIDOperations(strafeX, scaledTurn);

        drive.mecanumDrive_Cartesian(-strafeX * orientation, strafeY * orientation, -scaledTurn, 0);
    }

    private void checkForOrientationChange()
    {
        //reverse orientation of control
        if (OI.primaryXboxRBButton())
        {
            changeDirection = true;
        }
        else if (changeDirection)
        {
            orientation = orientation * -1;
            changeDirection = false;
        }
    }

    public void checkForPIDButton()
    {
        if (OI.primaryXboxYButton())
        {
            toggleButton = true;
        }
        else if (toggleButton)
        {
            toggleButton = false;
            buttonTogglePIDOff = !buttonTogglePIDOff;
        }
    }

    private double clampMotorValues(double scaledStrafe)
    {
        //double scaledLeftStrafe = OI.getUsedLeftX() * 1.25 * OI.getSensitivity();

        if (scaledStrafe > 1)
        {
            scaledStrafe = 1;
        }
        if (scaledStrafe < -1)
        {
            scaledStrafe = -1;
        }
        return scaledStrafe;
    }

    /*
     * Takes care of all PID code and if the PID operations cshould be used, it
     * applies the operations to the values given in the parameter.
     */
    public double checkAndRunPIDOperations(double strafe, double rotation)
    {
        double finalRotation = 0;
        //P:.01
        //I:.001
        //D:0
        if (pidController == null)
        {
            return rotation;
        }

        pidController.setPID(OI.getAnalogIn(1), OI.getAnalogIn(2), OI.getAnalogIn(3));

        if (!pidController.isEnable())//Enables PID
        {
            pidController.enable();
        }

        //Checks if the toggle button is pressed, and if it is, it toggles PID enabled
        checkForPIDButton();

        //disables PID if the toggle is on
        if (buttonTogglePIDOff)
        {
            PIDControl = false;
        }
        else
        {
            if (Math.abs(strafe) < .05) // if not trying to strafe
            {
                PIDControl = false;
            }
            else if (!PIDControl) // if trying to strafe, and it is the first iteration of doing so
            {
                pidController.setSetpoint(horizontalGyro.getAngle());
                PIDControl = true;
            }
        }

        if (PIDControl)
        {
            finalRotation = rotationPID.getValue();
        }
        return finalRotation;
    }

    public void autoAimMechanum(PacketListener kinecter)
    {
        double scaledRightStrafe = OI.getUsedRightX() * 1.25 * OI.getSensitivity();
        double scaledLeftStrafe = OI.getUsedLeftX() * 1.25 * OI.getSensitivity();

        if (scaledRightStrafe > 1)
        {
            scaledRightStrafe = 1;
        }

        if (scaledLeftStrafe > 1)
        {
            scaledLeftStrafe = 1;
        }

        double scaledLeftTurn = 0;
        double scaledRightTurn = 0;
        //correct for strafing code
        if (Math.abs(kinecter.getOffAxis()[0]) > 5)
        {
            scaledLeftTurn = ((1 / 320) * (kinecter.getDepth()[0] / oneForOneDepth) * kinecter.getOffAxis()[0] / 320 * (strafeCorrectionFactor * scaledRightStrafe));  // forces slight turn
            scaledRightTurn = ((1 / 320) * (kinecter.getDepth()[0] / oneForOneDepth) * kinecter.getOffAxis()[0] / 320 * (strafeCorrectionFactor * scaledLeftStrafe));
        }

        if (scaledLeftTurn > 1)
        {
            scaledLeftTurn = 1;
        }
        else if (scaledLeftTurn < -1)
        {
            scaledLeftTurn = -1;
        }

        if (scaledRightTurn > 1)
        {
            scaledRightTurn = 1;
        }
        else if (scaledRightTurn < -1)
        {
            scaledRightTurn = -1;
        }

        if (OI.rightStrafe())
        {
            drive.mecanumDrive_Cartesian(-scaledRightStrafe, (OI.getUsedRightY() * OI.getSensitivity()), scaledLeftTurn, 0); //if right hand stick is being used for strafing left, right, up and down
        }
        else                       // if left hand stick is being used for strafing
        {
            drive.mecanumDrive_Cartesian(-scaledLeftStrafe, (OI.getUsedLeftY() * OI.getSensitivity()), scaledRightTurn, 0);
        }
    }

    /*
     * Used for cartesian control of a mechanum drive Status: Untested
     */
    public void autoMechanumDrive(double x, double y, double rotation)
    {
        drive.mecanumDrive_Cartesian(x, y, rotation, 0);
        if (rotation > 0)
        {
            horizontalGyro.reset();
        }
    }

    /*
     * @param mag the speed desired to be moved, theta the angle that the robot
     * will move towards, rotation the speed at which the robot is turning
     *
     * Moves the robot using polar coordinates - takes in three components and
     * moves the robot accordingly Status: Untested
     */
    public void polarMechanum(double mag, double theta, double rotation)
    {
        drive.mecanumDrive_Polar(mag, theta, rotation);
    }

    /*
     * Tank drives using joystick controls, sets left side to Y value of left
     * joystick and right side as Y value of right joystick Status:Tested for
     * both xbox + joysticks
     */
    public void tankDrive()
    {
        lFront.set(-OI.getUsedLeftY()); //deadzone(OI.leftJoystick.getY()));
        lBack.set(-OI.getUsedLeftY()); //-deadzone(OI.leftJoystick.getY()));

        rFront.set(OI.getUsedRightY()); //deadzone(OI.rightJoystick.getY()));
        rBack.set(OI.getUsedRightY()); //deadzone(OI.rightJoystick.getY()));
        System.out.println("Tank: " + OI.getUsedLeftY());

    }

    /*
     * Tank drives using two doubles, left side speed and right speed Status:
     * untested
     */
    public void tankDrive(double left, double right)
    {
        lFront.set(left); //deadzone(OI.leftJoystick.getY()));
        lBack.set(left); //-deadzone(OI.leftJoystick.getY()));

        rFront.set(right); //deadzone(OI.rightJoystick.getY()));
        rBack.set(right);
    }

    /*
     * This method may or may not be used depending on whether we use an
     * accelerometer @return the accelerometer's value
     */
    public double getMovement()
    {
        return accel.getAcceleration();
    }

    /*
     * Right now, this will not be called because we don't have a gyro hooked
     * up. However, it will be used in autonomous, so we might as well keep it
     * 2/6/12
     */
    public void resetGyro()
    {
        horizontalGyro.reset();
    }

    public void resetRequestedAngle()
    {
        pidController.reset();
        requestedAngle = 0;
        rotationPID.output = 0;
        pidController.enable();
    }

    public void initDefaultCommand()
    {
        setDefaultCommand(new MechanumDrive());
    }

    public class SoftPID implements PIDOutput
    {

        double output = 0;

        public SoftPID()
        {
            super();
            pidController = new PIDController(OI.getAnalogIn(1), OI.getAnalogIn(2), OI.getAnalogIn(3), horizontalGyro, rotationPID);
            pidController.setTolerance(.05);
        }

        public double getValue()
        {
            return this.output;
        }

        public void pidWrite(double output)
        {
            this.output = output;
        }
    }
}
