package com.android.tools.profiler;

import com.android.tools.profiler.proto.Common.*;
import com.android.tools.profiler.proto.EventProfiler.ActivityDataResponse;
import com.android.tools.profiler.proto.EventProfiler.EventDataRequest;
import com.android.tools.profiler.proto.EventServiceGrpc;
import com.android.tools.profiler.proto.MemoryServiceGrpc;
import com.android.tools.profiler.proto.NetworkServiceGrpc;
import com.android.tools.profiler.proto.Profiler;
import com.android.tools.profiler.proto.ProfilerServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.junit.Assert;

/**
 * Test class for managing a connection to perfd.
 */
public class GrpcUtils {

    private final ManagedChannel myChannel;
    private final ProfilerServiceGrpc.ProfilerServiceBlockingStub myProfilerServiceStub;
    private final EventServiceGrpc.EventServiceBlockingStub myEventServiceStub;
    private final NetworkServiceGrpc.NetworkServiceBlockingStub myNetworkServiceStub;
    private final MemoryServiceGrpc.MemoryServiceBlockingStub myMemoryServiceStub;

    /**
     * Connect to perfd using a socket and port, currently abstract sockets are not supported.
     */
    public GrpcUtils(String socket, int port) {
        myChannel = connectGrpc(socket, port);
        myProfilerServiceStub = ProfilerServiceGrpc.newBlockingStub(myChannel);
        myEventServiceStub = EventServiceGrpc.newBlockingStub(myChannel);
        myNetworkServiceStub = NetworkServiceGrpc.newBlockingStub(myChannel);
        myMemoryServiceStub = MemoryServiceGrpc.newBlockingStub(myChannel);
    }

    public ProfilerServiceGrpc.ProfilerServiceBlockingStub getProfilerStub() {
        return myProfilerServiceStub;
    }

    public EventServiceGrpc.EventServiceBlockingStub getEventStub() {
        return myEventServiceStub;
    }

    public NetworkServiceGrpc.NetworkServiceBlockingStub getNetworkStub() {
        return myNetworkServiceStub;
    }

    public MemoryServiceGrpc.MemoryServiceBlockingStub getMemoryStub() {
        return myMemoryServiceStub;
    }

    private ManagedChannel connectGrpc(String socket, int port) {
        ClassLoader stashedContextClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(ManagedChannelBuilder.class.getClassLoader());
        ManagedChannel channel =
            ManagedChannelBuilder.forAddress(socket, port).usePlaintext(true).build();
        Thread.currentThread().setContextClassLoader(stashedContextClassLoader);
        return channel;
    }

    /**
     * Support function to get the running process ID of the perfa application. If no process is
     * running this function will assert.
     */
    public int getProcessId() {
        Profiler.GetProcessesResponse process =
            myProfilerServiceStub.getProcesses(
                Profiler.GetProcessesRequest.getDefaultInstance());
        Assert.assertEquals(1, process.getProcessCount());
        return process.getProcess(0).getPid();
    }

    /**
     * Support function to get the main activity. This function checks for the activity name, if it
     * is not the default activity an assert will be thrown.
     */
    public ActivityDataResponse getActivity(Session session) {
        ActivityDataResponse response =
            myEventServiceStub.getActivityData(
                EventDataRequest.newBuilder().setSession(session).build());
        return response;
    }
}
