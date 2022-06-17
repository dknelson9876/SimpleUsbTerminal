package de.kai_morich.simple_usb_terminal;

import android.os.Build;
import android.os.Bundle;

import androidx.annotation.RequiresApi;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

@RequiresApi(api = Build.VERSION_CODES.O)
public class TwoDeviceFragment extends Fragment {


    // TODO: Rename and change types of parameters
    private String mParam1;
    private String mParam2;

    public TwoDeviceFragment() {
        // Required empty public constructor
    }


//    @Override
//    public void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        if (getArguments() != null) {
//            mParam1 = getArguments().getString(ARG_PARAM1);
//            mParam2 = getArguments().getString(ARG_PARAM2);
//        }
//    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_two_device, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState){
//        Toast.makeText(getActivity(), "TwoDevice.onViewCreated()", Toast.LENGTH_SHORT).show();
        savedInstanceState = getArguments();
        Fragment terminal1 = null;
        try {
            terminal1 = new TerminalFragment();
            Toast.makeText(getActivity(), "Instantiated terminal1", Toast.LENGTH_SHORT).show();
            Toast.makeText(getActivity(), "Bundle: "+(savedInstanceState != null), Toast.LENGTH_SHORT).show();
            terminal1.setArguments(savedInstanceState.getBundle("args1"));
        } catch (Exception e) {
            Toast.makeText(getActivity(), e.getMessage(), Toast.LENGTH_SHORT).show();
        }
        Toast.makeText(getActivity(), "Created terminal1", Toast.LENGTH_SHORT).show();
        Fragment terminal2 = new TerminalFragment();
        terminal2.setArguments(savedInstanceState.getBundle("args2"));
        Toast.makeText(getActivity(), "Created terminal2", Toast.LENGTH_SHORT).show();
        getChildFragmentManager().beginTransaction().replace(R.id.fragment1, terminal1).commit();
        getChildFragmentManager().beginTransaction().replace(R.id.fragment2, terminal2).commit();
    }
}