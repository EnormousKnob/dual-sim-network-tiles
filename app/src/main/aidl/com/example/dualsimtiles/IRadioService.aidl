package com.example.dualsimtiles;
import android.os.Bundle;
interface IRadioService {
    Bundle execute(int action) = 0;
    Bundle request(in Bundle request) = 1;
    void destroy() = 16777114;
}
