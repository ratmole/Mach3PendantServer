package gr.ratmole.android.Mach3PendantServer;
import com.sun.jna.Library;

public interface Mach3 extends Library {
	
	//extern "C" __declspec(dllexport) void SetGetDRO(DoubleShort pFunc)         // double GetDRO( short code );
	//{
	//   GetDRO = pFunc;
	//}
	
	double GetDRO(double code);
}
