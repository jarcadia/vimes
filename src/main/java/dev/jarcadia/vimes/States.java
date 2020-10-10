package dev.jarcadia.vimes;

public class States {
	
	public enum InstanceState {
	    Enabled,
	    Draining,
	    Disabled,
	    Down,
	}
	
	public enum DistributionState {
	    PendingTransfer,
	    Transferring,
	    Transferred,

	    PendingCleanup,
	    CleaningUp,
	    CleanedUp
	}

	public enum GroupState {
		Up,
		Partial,
		Down
	}

	public enum CheckState {
		Pass,
		Fail
	}
}
