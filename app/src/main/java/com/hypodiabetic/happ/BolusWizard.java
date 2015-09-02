
package com.hypodiabetic.happ;

import android.content.Context;
import android.widget.Toast;

import com.hypodiabetic.happ.code.nightwatch.Bg;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;
import java.util.List;

public class BolusWizard {


    //main functiuon
    public static JSONObject run_bw(Context context) {

        Date dateNow = new Date();
        Profile profile = new Profile().ProfileAsOf(dateNow, context);
        List treatments = Treatments.latestTreatments(20, "Insulin");

        JSONObject bwp = bwp_calc(treatments, profile, dateNow);
        JSONObject reply = pushInfo(bwp);

        return reply;

    }



    public static JSONObject bwp_calc(List treatments, Profile profile, Date dateNow) {

        JSONObject results = new JSONObject();
        try {
            results.put("effect",0);
            results.put("outcome",0);
            results.put("bolusEstimate",0.0);
        } catch (JSONException e) {
        }

        Bg scaled = Bg.last();

        Double results_scaledSGV = scaled.sgv_double();

        //var errors = checkMissingInfo(sbx);

        //if (errors && errors.length > 0) {
        //    results.errors = errors;
        //    return results;
        //}

        Double iob=0D;
        try {
            iob = com.hypodiabetic.happ.code.openaps.iob.iobTotal(treatments, profile, dateNow).getDouble("iob");
        } catch (JSONException e) {
            //Toast.makeText(ApplicationContextProvider.getContext(), "Error getting IOB for bwp_calc", Toast.LENGTH_LONG).show();
        }

        Double results_effect = iob * profile.isf;
        Double results_outcome = scaled.sgv_double() - results_effect;
        Double delta = 0D;

        Double target_high = profile.max_bg;
        Double sens = profile.isf;

        Double results_bolusEstimate = 0D;
        Double results_aimTarget=0D;
        String results_aimTargetString="";

        if (results_outcome > target_high) {
            delta = results_outcome - target_high;
            results_bolusEstimate = delta / sens;
            results_aimTarget = target_high;
            results_aimTargetString = "above high";
        }

        Double target_low = profile.min_bg;

        if (results_outcome < target_low) {
            delta = Math.abs(results_outcome - target_low);
            results_bolusEstimate = delta / sens * -1;
            results_aimTarget = target_low;
            results_aimTargetString = "below low";
        }

        if (results_bolusEstimate != 0 && profile.current_basal != 0) {
            // Basal profile exists, calculate % change
            Double basal = profile.current_basal;

            Double thirtyMinAdjustment  = (double) Math.round((basal/2 + results_bolusEstimate) / (basal / 2) * 100);
            Double oneHourAdjustment    = (double) Math.round((basal + results_bolusEstimate) / basal * 100);

            // TODO: 02/09/2015 this should be in a sub JSON object called tempBasalAdjustment
            try {
                results.put("tempBasalAdjustment-thirtymin",thirtyMinAdjustment);
                results.put("tempBasalAdjustment-onehour",oneHourAdjustment);
            } catch (JSONException e) {
            }
        }

        try {
            results.put("effect",results_effect);
            results.put("outcome",results_outcome);
            results.put("bolusEstimate",results_bolusEstimate);
            results.put("aimTarget",results_aimTarget);
            results.put("aimTargetString",results_aimTargetString);
            results.put("scaledSGV",results_scaledSGV);
            results.put("iob",iob);

            results.put("bolusEstimateDisplay", String.format("%.2f",results_bolusEstimate));
            results.put("outcomeDisplay", String.format("%.2f",results_outcome));
            results.put("displayIOB", String.format("%.2f",iob));
            results.put("effectDisplay", String.format("%.2f",results_effect));
            results.put("displayLine", "BWP: " + String.format("%.2f",results_bolusEstimate) + "U");
        } catch (JSONException e) {
        }

        return results;
    }

    public static JSONObject pushInfo(JSONObject prop) {
        //if (prop && prop.errors) {
        //    info.push({label: 'Notice', value: 'required info missing'});
        //    _.forEach(prop.errors, function pushError (error) {
        //        info.push({label: '  • ', value: error});
        //    });
        //} else if (prop) {

        Date dateNow = new Date();
        Profile profile = new Profile().ProfileAsOf(dateNow, ApplicationContextProvider.getContext());

        JSONObject results = new JSONObject();
        try {
            results.put("Insulin on Board", prop.getString("displayIOB") + "U");
            results.put("Sensitivity", "-" + profile.isf + " /U");
            results.put("Expected effect", prop.getString("displayIOB") + " x -" + profile.isf + "= - " + prop.getString("effectDisplay") );
            results.put("Expected outcome", prop.getString("scaledSGV") + "-" + prop.getString("effectDisplay") + " = " + prop.getString("outcomeDisplay"));

            // TODO: 02/09/2015 these items should be put at the top of the JSON object, poss in reverse order
            if (prop.getDouble("bolusEstimate") < 0) {
                //info.unshift({label: '---------', value: ''});
                Double carbEquivalent = Math.ceil(Math.abs(profile.carbRatio * prop.getDouble("bolusEstimate")));
                results.put("Carb Equivalent", prop.getString("bolusEstimateDisplay") + "U * " + profile.carbRatio + " = " + carbEquivalent + "g");
                results.put("Current Carb Ratio", "1U / " + profile.carbRatio + "g");
                results.put("-BWP", prop.getString("bolusEstimateDisplay") + "U, maybe covered by carbs?");
            }

        } catch (JSONException e) {
        }

        return results;

        //} else {
        //    info.push({label: 'Notice', value: 'required info missing'});
        //}

        // TODO: 02/09/2015 this function appears to give bolus suggestions, maybe intresting to compare with OpenAPS
        //pushTempBasalAdjustments(prop, info, sbx);
    }

}